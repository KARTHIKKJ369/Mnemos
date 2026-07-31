# PhotoVault

Self-hosted photo and video backup server designed for private Tailscale networks.

## Run

Install Go 1.24 or newer, then run:

```sh
go mod tidy
go run ./cmd/photovault
```

Run the automated tests with:

```sh
go test ./...
```

The server listens on `127.0.0.1:8080` by default and creates the following under `storage/`:

```text
storage/
├── blobs/
├── derived/
│   ├── previews/
│   └── thumbnails/
├── .locked/
└── vault.db
```

## Configuration

| Variable | Default | Description |
| --- | --- | --- |
| `PHOTOVAULT_HTTP_ADDRESS` | `127.0.0.1:8080` | HTTP listen address; use a Tailscale-reachable address when deploying. |
| `PHOTOVAULT_STORAGE_PATH` | `storage` | Root directory for blobs, derived files, locked files, and SQLite database. |
| `PHOTOVAULT_LOG_LEVEL` | `info` | Structured-log level: `debug`, `info`, `warn`, or `error`. |
| `PHOTOVAULT_SHUTDOWN_TIMEOUT` | `15s` | Maximum graceful shutdown duration. |
| `PHOTOVAULT_MAX_UPLOAD_BYTES` | `5368709120` | Maximum total multipart upload request size in bytes (5 GiB by default). |
| `PHOTOVAULT_HASH_CACHE_SIZE` | `1024` | Maximum cached positive hash-existence results; `0` disables caching. |
| `PHOTOVAULT_HASH_CACHE_TTL` | `5m` | Time a cached positive hash-existence result remains valid; `0s` disables caching. |
| `PHOTOVAULT_SYNC_DEFAULT_LIMIT` | `100` | Default page size for `GET /sync/diff`. |
| `PHOTOVAULT_SYNC_MAX_LIMIT` | `1000` | Maximum allowed page size for `GET /sync/diff`. |
| `PHOTOVAULT_SYNC_ACK_MAX_BATCH` | `500` | Maximum number of file IDs accepted by `POST /sync/ack`. |

## Health check

```sh
curl -i http://127.0.0.1:8080/health
```

Expected body:

```json
{"status":"ok"}
```

## Device registration

Register each client once. The returned `auth_token` is shown only in this response; clients must store it securely.

```sh
curl -i -X POST http://127.0.0.1:8080/devices/register \
  -H 'Content-Type: application/json' \
  --data '{"name":"Karthik’s iPhone","device_type":"ios"}'
```

Expected response shape:

```json
{
  "device_id": "a UUID",
  "auth_token": "a cryptographically random bearer token"
}
```

Use that token for future protected endpoints:

```http
Authorization: Bearer <auth_token>
```

Only the SHA-256 hash of the token is stored in SQLite. Supported device types are `ios`, `android`, `mac`, and `web`.

## Download original media

Download an original photo or video by its `file_id`. Authentication, conditional requests, and HTTP byte ranges are supported, so video clients can seek without downloading the full blob.

```sh
curl -i http://127.0.0.1:8080/media/<file_id>/original \
  -H "Authorization: Bearer <auth_token>" \
  -o original-media
```

Resume or request a segment of a large video:

```sh
curl -i http://127.0.0.1:8080/media/<file_id>/original \
  -H "Authorization: Bearer <auth_token>" \
  -H 'Range: bytes=1048576-2097151' \
  -o video-part
```

The response includes the detected `Content-Type`, `Content-Length`, a quoted SHA-256 `ETag`, and `Last-Modified` from the upload timestamp. A missing file ID returns `404` with `file_not_found`; missing or invalid credentials return `401`.

## Media search and metadata

Media search uses the SQLite `media_index` projection, populated asynchronously after uploads. Searches use database indexes and never scan the blob filesystem. The projection stores filenames, MIME type, size, dimensions, timestamps, camera/GPS fields when extractable, derived-media availability, favorites, and soft-delete state.

```text
files (original metadata) 1 ─── 1 media_index (search projection)
       │                              │
       └── media_index_jobs ──────────┘ background metadata worker
```

```sh
curl -i 'http://127.0.0.1:8080/media?query=beach&from=2024-01-01&to=2024-12-31&limit=50&sort=taken_at&order=desc' \
  -H "Authorization: Bearer <auth_token>"

curl -i http://127.0.0.1:8080/media/<file_id> \
  -H "Authorization: Bearer <auth_token>"

curl -i -X POST http://127.0.0.1:8080/media/<file_id>/favorite \
  -H "Authorization: Bearer <auth_token>"

curl -i -X DELETE http://127.0.0.1:8080/media/<file_id> \
  -H "Authorization: Bearer <auth_token>"
```

Filters include `query`, `mime_type`, `from`, `to`, `favorite`, `has_thumbnail`, and `has_preview`; pagination uses `limit` and `offset`. Supported sort fields are `filename`, `taken_at`, `mime_type`, and `uploaded_at`. Soft-deleted media remains stored but is excluded from searches and metadata responses.

## Upload

Upload one file using the authenticated `file` multipart field:

```sh
curl -i -X POST http://127.0.0.1:8080/upload \
  -H "Authorization: Bearer <auth_token>" \
  -F 'file=@/absolute/path/to/photo.jpg'
```

The server streams data to a temporary file, calculates SHA-256 during transfer, detects MIME type from bytes, and atomically stores unique blobs under `storage/blobs/by-device/`.

## Derived media processing

Successful uploads enqueue durable background work; upload responses never wait for thumbnail or preview generation. The worker creates JPEG thumbnails under `storage/thumbnails/`. When `ffmpeg` is installed, supported videos also receive a JPEG frame thumbnail and an H.264/AAC 480p MP4 preview under `storage/previews/`. Unsupported formats, unavailable decoders, and installations without `ffmpeg` are skipped without affecting the original upload.

```text
POST /upload
    │ stream + SHA-256 + atomic original blob
    ▼
SQLite file metadata + media_processing_jobs
    ▼
background worker ──► thumbnail JPEG ──► storage/thumbnails
    │
    └──► ffmpeg video preview ──► storage/previews
                                      │
GET /media/{id}/thumbnail or /preview ◄┘
```

Derived endpoints require bearer authentication and return `404 media_not_ready` until the worker has committed the generated file metadata.

```sh
curl -i http://127.0.0.1:8080/media/<file_id>/thumbnail \
  -H "Authorization: Bearer <auth_token>" \
  -o thumbnail.jpg

curl -i http://127.0.0.1:8080/media/<file_id>/preview \
  -H "Authorization: Bearer <auth_token>" \
  -H 'Range: bytes=0-1048575' \
  -o preview-part.mp4
```

## Hash existence check

Clients should check a locally computed SHA-256 before uploading. Authentication is required.

```sh
curl -i 'http://127.0.0.1:8080/files/exists?hash=<sha256-hex>' \
  -H "Authorization: Bearer <auth_token>"
```

Existing file response:

```json
{
  "exists": true,
  "file_id": "uuid",
  "size_bytes": 12345
}
```

Missing file response:

```json
{
  "exists": false
}
```

If the file already exists, call `POST /sync/ack` so the authenticated device records that it has the content locally without uploading bytes.

## Sync diff

List metadata for files the authenticated device has not synchronized yet. The device identity comes from the bearer token; clients must not send a `device_id`.

```sh
curl -i 'http://127.0.0.1:8080/sync/diff?since=0&limit=100' \
  -H "Authorization: Bearer <auth_token>"
```

Query parameters:

| Parameter | Required | Description |
| --- | --- | --- |
| `since` | No | Unix timestamp in milliseconds. When omitted, the diff starts from the beginning. When provided, only files uploaded after this timestamp are returned. |
| `limit` | No | Page size. Defaults to `PHOTOVAULT_SYNC_DEFAULT_LIMIT` and must not exceed `PHOTOVAULT_SYNC_MAX_LIMIT`. |

Success response:

```json
{
  "files": [
    {
      "file_id": "uuid",
      "hash": "sha256-hex",
      "filename": "photo.jpg",
      "mime_type": "image/jpeg",
      "size_bytes": 12345,
      "thumbnail_available": false,
      "preview_available": false,
      "uploaded_at": 1710000000000
    }
  ],
  "next_since": 1710000000000
}
```

When there are no remaining files, the response is:

```json
{
  "files": []
}
```

Results are ordered deterministically by `uploaded_at` ascending, then `file_id` ascending. Filesystem paths are never returned.

Error responses:

| Status | Code | When |
| --- | --- | --- |
| `400` | `invalid_since` | `since` is negative or not an integer |
| `400` | `invalid_limit` | `limit` is zero, negative, or above the configured maximum |
| `401` | `unauthorized` | Missing or invalid bearer token |

## Sync ack

Record that the authenticated device has synchronized one or more files locally. Retries are idempotent.

```sh
curl -i -X POST http://127.0.0.1:8080/sync/ack \
  -H "Authorization: Bearer <auth_token>" \
  -H 'Content-Type: application/json' \
  --data '{"file_ids":["uuid-1","uuid-2"]}'
```

Success response:

```json
{
  "acknowledged": 2
}
```

Validation rules:

- `file_ids` must contain at least one UUID
- Duplicate IDs are rejected
- Unknown file IDs are rejected
- Batch size must not exceed `PHOTOVAULT_SYNC_ACK_MAX_BATCH`

Error responses:

| Status | Code | When |
| --- | --- | --- |
| `400` | `invalid_request` | Empty batch, duplicate IDs, invalid UUIDs, unknown file IDs, or batch too large |
| `401` | `unauthorized` | Missing or invalid bearer token |

## Client synchronization engine

The reusable `photovault/syncclient` package implements a durable pull client without changing any backend endpoint. Configure it with the server URL, device token, a local SQLite path, download directory, temporary directory, worker count, retry policy, and an optional progress-event callback. `SyncOnce` completes one paginated catch-up; `Run` repeats it at the configured interval for offline recovery.

```text
Client                 Server                 Local disk / SQLite
  │ GET /sync/diff       │
  ├─────────────────────►│
  │◄── file page ────────┤
  │                      │
  │ GET /media/{id}/original (Range when .part exists)
  ├─────────────────────►│
  │◄── streamed bytes ───┤──► temporary file
  │ SHA-256 verify + atomic rename ───────────► local_files
  │ POST /sync/ack       │
  ├─────────────────────►│
  │◄── acknowledged ────┤──► mark acknowledged + advance next_since
```

`next_since` advances only after every item in a diff page is fully downloaded, verified, atomically renamed, and acknowledged. Interrupted downloads retain their `.part` file and resume with HTTP ranges on the next attempt.

## Two-device sync example

Register two devices, upload from device A, then synchronize device B:

```sh
# Device A
curl -s -X POST http://127.0.0.1:8080/devices/register \
  -H 'Content-Type: application/json' \
  --data '{"name":"Device A","device_type":"ios"}'

curl -s -X POST http://127.0.0.1:8080/upload \
  -H "Authorization: Bearer <device_a_token>" \
  -F 'file=@/absolute/path/to/photo.jpg'

# Device B
curl -s -X POST http://127.0.0.1:8080/devices/register \
  -H 'Content-Type: application/json' \
  --data '{"name":"Device B","device_type":"mac"}'

curl -s 'http://127.0.0.1:8080/sync/diff' \
  -H "Authorization: Bearer <device_b_token>"

curl -s -X POST http://127.0.0.1:8080/sync/ack \
  -H "Authorization: Bearer <device_b_token>" \
  -H 'Content-Type: application/json' \
  --data '{"file_ids":["<file_id_from_diff>"]}'

curl -s 'http://127.0.0.1:8080/sync/diff' \
  -H "Authorization: Bearer <device_b_token>"
```

After ack, the second diff returns an empty `files` array for that content.
