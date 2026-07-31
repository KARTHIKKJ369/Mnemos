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

## Upload

Upload one file using the authenticated `file` multipart field:

```sh
curl -i -X POST http://127.0.0.1:8080/upload \
  -H "Authorization: Bearer <auth_token>" \
  -F 'file=@/absolute/path/to/photo.jpg'
```

The server streams data to a temporary file, calculates SHA-256 during transfer, detects MIME type from bytes, and atomically stores unique blobs under `storage/blobs/by-device/`.
