# PhotoVault

Self-hosted photo and video backup server designed for private Tailscale networks.

### Run as Background Service (Single Command)

To build and run PhotoVault permanently in the background (starts on login, auto-restarts on crash, serves both API + React Web UI on port 8080):

```sh
make install
# or
./scripts/install.sh
```

Service management:
- **Start**: `make start` or `launchctl start com.photovault.server`
- **Stop**: `make stop` or `launchctl stop com.photovault.server`
- **Logs**: `make logs` or `tail -f ~/Library/Logs/PhotoVault/photovault.log`
- **Uninstall**: `make uninstall` or `./scripts/install.sh --uninstall`

### Development Mode

Run backend and frontend independently during development:

```sh
# Backend
go run ./cmd/photovault

# Frontend (in another terminal)
cd mnemos-web && pnpm dev
```

The server automatically loads environment variables from a `.env` file if present in the workspace root. You can configure your vault storage path in `.env`:

```env
PHOTOVAULT_STORAGE_PATH="/Volumes/ExternalDrive/PhotoVaultStorage"
```

Or configure and persist it directly from the Web UI (**Settings** → **Vault Storage Location**).

You can also override the storage location dynamically with the `-storage` flag:

```sh
go run ./cmd/photovault -storage /Volumes/ExternalDrive/PhotoVaultStorage
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
{
  "version": "1.0.0",
  "build_commit": "abcdef1",
  "uptime_seconds": 3600,
  "database": "ok",
  "blob_storage": "ok",
  "workers": "ok",
  "storage_path": "/var/lib/photovault/blobs",
  "total_media": 1420,
  "total_photos": 1280,
  "total_videos": 140,
  "vault_bytes": 4831838208,
  "total_devices": 3,
  "disk_free_bytes": 107374182400,
  "disk_total_bytes": 500107862016
}
```

## Device registration

Register each client once. The returned `auth_token` is shown only in this response; clients must store it securely.

```sh
curl -i -X POST http://127.0.0.1:8080/devices/register \
  -H 'Content-Type: application/json' \
  --data '{"name":"My iPhone","device_type":"ios"}'
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

### List registered devices

```sh
curl -i http://127.0.0.1:8080/devices \
  -H "Authorization: Bearer <auth_token>"
```

### Unregister & remove device

Removes an authorized client device. Any media previously uploaded by the device is safely preserved and reassigned to the server host. The primary host admin device cannot be deleted.

```sh
curl -i -X DELETE http://127.0.0.1:8080/devices/<device_id> \
  -H "Authorization: Bearer <auth_token>"
```

## Download original media

Download an original photo or video by its `file_id`. Authentication, conditional requests, and HTTP byte ranges are supported, so video clients can seek without downloading the full blob.

```sh
curl -i http://127.0.0.1:8080/media/<file_id>/original \
  -H "Authorization: Bearer <auth_token>" \
  -o original-media
```

Add `?download=1` to force a file download with `Content-Disposition: attachment; filename="<original_filename>"`:

```sh
curl -i 'http://127.0.0.1:8080/media/<file_id>/original?download=1' \
  -H "Authorization: Bearer <auth_token>" \
  -O -J
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

Filters include `query`, `mime_type`, `from`, `to`, `favorite`, `has_thumbnail`, `has_preview`, `device_id` (filter media uploaded by a specific client), and `exclude_device_id` (filter media uploaded by other clients); pagination uses `limit` and `offset`. Supported sort fields are `filename`, `taken_at`, `mime_type`, and `uploaded_at`. Soft-deleted media remains stored but is excluded from searches and metadata responses.

Media search responses include uploader identity (`uploaded_by_device_id`, `uploaded_by_device_name`, `uploaded_by_device_type`) so clients can distinguish local vs remote files.

## Upload

Upload one file using the authenticated `file` multipart field:

```sh
curl -i -X POST http://127.0.0.1:8080/upload \
  -H "Authorization: Bearer <auth_token>" \
  -F 'file=@/absolute/path/to/photo.jpg'
```

The server streams data to a temporary file, calculates SHA-256 during transfer, detects MIME type from bytes, and atomically stores unique blobs under `storage/blobs/by-device/`.

## Derived media processing & Hardware Acceleration

Successful uploads enqueue durable background jobs; upload responses never wait for thumbnail or preview generation. The background processor leverages Apple Silicon hardware engines when running on macOS:
- **Photos (720px HD)**: Uses macOS `sips` (CoreImage/Metal) to generate high-definition 720px JPEG thumbnails with near-zero CPU usage, falling back to pure Go Catmull-Rom scaling when `sips` is unavailable.
- **Videos (1080p MP4 & Poster)**: Uses `ffmpeg` with Apple VideoToolbox hardware acceleration (`h264_videotoolbox` / `hevc_videotoolbox`) for ultra-fast 1080p CRF 20 preview generation and instant poster extraction.

```text
POST /upload
    │ stream + SHA-256 + atomic original blob
    ▼
SQLite file metadata + media_processing_jobs
    ▼
background worker ──► macOS sips / CoreImage ──► 720px HD thumbnail
    │
    └──► VideoToolbox hardware ffmpeg ──► 1080p MP4 preview
                                              │
GET /media/{id}/thumbnail or /preview ◄───────┘
```

Derived endpoints require bearer authentication, support HTTP byte-ranges, and include immutable caching headers (`Cache-Control: public, max-age=31536000, immutable`):

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

## Tailscale Network & Auth Bootstrap

PhotoVault restricts API access to private networks (Localhost, private LAN, and Tailscale CGNAT `100.64.0.0/10`).

When accessing the web app from the host machine (`127.0.0.1` / `localhost`), the frontend calls `GET /auth/bootstrap`:

```sh
curl -i http://127.0.0.1:8080/auth/bootstrap
```

Response for localhost:
```json
{
  "is_admin": true,
  "device_id": "<admin_uuid>",
  "auth_token": "<admin_token>",
  "device_name": "Server Host (Admin)",
  "device_type": "mac",
  "network_allowed": true
}
```

Response for remote Tailscale devices:
```json
{
  "is_admin": false,
  "network_allowed": true
}
```

Remote client devices register effortlessly via `POST /devices/register` with their device name and type (`ios`, `android`, `mac`, `web`).

## Storage Folder Ingestion & Scanner

To ingest existing photos or videos located in a server folder (or the server's storage directory) that have no database IDs:

```sh
curl -i -X POST http://127.0.0.1:8080/storage/scan \
  -H "Authorization: Bearer <auth_token>" \
  -H 'Content-Type: application/json' \
  --data '{"path":"/Volumes/Photos/Archive"}'
```

Leave `path` empty or omitted to scan the server's configured storage directory. The scanner calculates SHA-256 hashes, deduplicates against existing records, inserts newly found files into SQLite, and enqueues thumbnail/preview/indexing workers:

```json
{
  "scanned": 142,
  "imported": 138,
  "already_indexed": 4,
  "errors": 0
}
```

## Native macOS Finder Folder Picker

To open the native macOS Finder directory picker on the server host to visually choose a folder:

```sh
curl -i -X POST http://127.0.0.1:8080/storage/pick-folder \
  -H "Authorization: Bearer <auth_token>"
```

Success response:
```json
{
  "cancelled": false,
  "path": "/Users/username/Pictures/VacationPhotos"
}
```

If the user cancels the dialog:
```json
{
  "cancelled": true
}
```

## Vault Storage Configuration (.env Persistence)

### Get Storage Config

```sh
curl -i http://127.0.0.1:8080/storage/config \
  -H "Authorization: Bearer <auth_token>"
```

Response:
```json
{
  "storage_path": "/Volumes/ExternalDrive/PhotoVaultStorage",
  "env_path": "/path/to/photoVault/.env",
  "env_exists": true
}
```

### Update Storage Config (.env)

```sh
curl -i -X POST http://127.0.0.1:8080/storage/config \
  -H "Authorization: Bearer <auth_token>" \
  -H 'Content-Type: application/json' \
  --data '{"storage_path":"/Volumes/MyPassport/PhotoVault"}'
```

Response:
```json
{
  "status": "saved",
  "storage_path": "/Volumes/MyPassport/PhotoVault",
  "requires_restart": true,
  "message": "Vault storage path saved to .env. Restart PhotoVault to load the new directory."
}
```

## Android Mobile App (Native Kotlin & Jetpack Compose)

PhotoVault includes a pure native Android companion application built in **Kotlin** with **Jetpack Compose**, **AndroidX Media3 (ExoPlayer)**, and **Coil**.

### Features
- **Progressive Streaming**: 720px thumbnail loaded immediately from cache, followed by seamless background cross-fade into full-resolution HD.
- **Hardware Video Player**: Media3 ExoPlayer with seekable byte ranges and auto-hiding playback controls.
- **Multi-Tier Caching**: 2GB persistent LRU disk cache + memory cache for instant offline browsing.
- **Native Haptics**: Tactile feedback on grid density pinch, photo taps, and upload completion.
- **Camera Roll Auto-Backup**: Android WorkManager background sync scanning device camera roll.

### Build APK
```sh
# Build debug APK
make apk
# or
cd photovault-android && ./gradlew assembleDebug
```

The APK will be generated at:
```
photovault-android/app/build/outputs/apk/debug/app-debug.apk
```

### Install APK to Connected Device
```sh
make install-apk
# or
adb install -r photovault-android/app/build/outputs/apk/debug/app-debug.apk
```



