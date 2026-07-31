PhotoVault
Self-hosted photo/video backup & sync — Technical Spec v1
Server: Go  |  Transport: Tailscale  |  Clients: Web (now), Native (later)

# 1. System Overview
A self-hosted alternative to Immich, scoped tightly to what you actually need: reliable device-to-Mac backup, cross-device access, dedup-aware sync, a locked/private area, and universal preview. No ML tagging, no federation, no bloat.
## 1.1 Components
| Component | Role |
|---|---|
| Mac Server (Go binary) | Single process: HTTP API, SQLite metadata store, file storage, thumbnail/transcode worker, auth |
| Tailscale | Private network mesh — every device gets a stable address to reach the Mac from anywhere, no port forwarding |
| Web Client | Browser-based upload, gallery, preview, locked-folder UI — works on phone/desktop identically |
| Native Clients (later) | iOS/Android apps for background auto-backup (camera roll watch), reusing the same API |

## 1.2 High-Level Flow
Device (phone/laptop)
   │  registers once → gets device_id + auth token
   ▼
Tailscale network (encrypted, no public exposure)
   │
   ▼
Mac Server (Go)
   ├─ /upload        → stores file, hashes it, writes DB row
   ├─ /sync/diff      → tells device what it's missing (dedup logic)
   ├─ /media          → serves files/thumbnails for preview
   └─ /locked/*       → gated behind re-auth
   │
   ▼
SQLite (metadata) + Filesystem (blobs, thumbnails)
# 2. Data Model (SQLite)
Content-addressed storage is the core idea: every file is identified by its SHA-256 hash, not its filename or path. This single decision solves dedup, cross-device sync, and duplicate detection all at once.
## 2.1 Tables
### devices
| Column | Type | Notes |
|---|---|---|
| id | TEXT PK | UUID, generated on first registration |
| name | TEXT | e.g. "Ashwin's iPhone", user-editable |
| device_type | TEXT | ios / android / mac / web |
| auth_token_hash | TEXT | hashed bearer token for this device |
| created_at | INTEGER | unix ms |
| last_seen_at | INTEGER | unix ms, updated per request |

### files
| Column | Type | Notes |
|---|---|---|
| id | TEXT PK | UUID |
| hash | TEXT UNIQUE | SHA-256 of file bytes — the dedup key |
| original_filename | TEXT | as sent by client |
| mime_type | TEXT | detected server-side, not trusted from client |
| size_bytes | INTEGER |  |
| uploaded_by_device_id | TEXT FK | device that first uploaded this content |
| storage_path | TEXT | relative path on disk, e.g. by-device/iphone-15/2026/07/<hash>.heic |
| thumbnail_path | TEXT NULL | generated async |
| preview_path | TEXT NULL | transcoded low-res preview for video/HEIC, async |
| captured_at | INTEGER NULL | from EXIF/metadata if available |
| uploaded_at | INTEGER | unix ms |
| is_locked | INTEGER | 0/1, default 0 |
| status | TEXT | uploading / processing / ready / failed |

### file_sync_state  (tracks what each device has pulled)
| Column | Type | Notes |
|---|---|---|
| device_id | TEXT FK | part of composite PK |
| file_id | TEXT FK | part of composite PK |
| synced_at | INTEGER | unix ms when this device confirmed it has the file locally |

This table is what makes "d2 shouldn't redownload what it already has" work — see §4.
# 3. Storage Layout
/storage
  /blobs
    /by-device/<device_slug>/<yyyy>/<mm>/<hash>.<ext>   ← original files, content-addressed
  /derived
    /thumbnails/<hash>.webp                              ← small, fast-loading grid thumbs
    /previews/<hash>.mp4 | <hash>.jpg                    ← transcoded/normalized previews
  /.locked                                                ← dot-prefixed + chflags hidden, excluded from TM/Spotlight
    /blobs/...                                            ← same structure, separate root, extra ACL check
  vault.db                                                ← SQLite
Filenames on disk are content hashes, not original names — this means the same photo uploaded twice (even from two different devices) is stored once. original_filename is preserved in the DB for display.
# 4. Sync & Dedup Protocol
This is the part that answers your exact question: "if d1 and d2 upload at the same time, and d2 wants everything d1 uploaded without re-downloading its own stuff."
## 4.1 Upload
Client computes SHA-256 of the file locally (or server computes it on receipt — client-side is better, avoids uploading duplicates over the wire at all).
Client calls GET /files/exists?hash=<hash> first. If the server already has it, client just calls POST /sync/ack (marks it as "synced" for that device) and skips the upload entirely — zero bytes transferred.
If not present, client does a resumable upload (tus protocol) to POST /upload.
Server stores the blob, inserts into files, and inserts a file_sync_state row for the uploading device immediately (it obviously already has what it just sent).
## 4.2 Sync-diff (pulling what you're missing)
Each device periodically (or on-demand) calls:
GET /sync/diff?device_id=d2&since=<last_sync_timestamp>
Server logic:
SELECT f.id, f.hash, f.storage_path, f.thumbnail_path, ...
FROM files f
WHERE f.uploaded_at > :since
  AND f.is_locked = 0
  AND f.id NOT IN (
    SELECT file_id FROM file_sync_state WHERE device_id = :device_id
  )
Because d2's own uploads already got a file_sync_state row at upload time (§4.1), this query naturally excludes them — d2 never re-downloads its own content. It only ever receives what other devices contributed. After d2 downloads a batch, it calls POST /sync/ack with the file IDs to record that it now has them.
## 4.3 Why hash-based sync beats a naive "list all files" approach
No duplicate storage even if two devices happen to capture/send the same file.
No redundant transfer — existence check happens before any bytes move.
Sync state is per-device, so a phone with limited storage can sync metadata/thumbnails only, and fetch full-res on demand (nice future option).
# 5. Locked Folder
Kept deliberately simple. Threat model: hide from casual Finder/Spotlight/backup browsing, not survive an attacker with terminal access to the Mac.
Files get is_locked = 1 and live under a dot-prefixed storage root: /storage/.locked -- hidden from Finder by default and from ls without -a.
Server also applies chflags hidden on that folder at startup (self-healing if the flag ever gets cleared): chflags hidden /storage/.locked
Time Machine exclusion, set once during setup: tmutil addexclusion /storage/.locked -- otherwise locked files still end up unencrypted in Time Machine backups.
Spotlight exclusion, set once during setup: add /storage/.locked under System Settings -> Siri & Spotlight -> Privacy (or mdutil -i off scoped to that path).
Accessing anything under /locked/* via the API requires a short-lived session token obtained via POST /locked/unlock, which re-verifies a passphrase (or device biometric prompt client-side, then a passphrase check server-side).
This unlock token expires after N minutes of inactivity and isn't the same as the normal device auth token -- losing your phone doesn't expose the locked folder.
Files in the locked folder never appear in normal gallery/sync-diff queries -- excluded at the SQL level (is_locked = 0 filter), not just hidden in the UI.
Known limitation: no encryption at rest. Anyone with Terminal access on the Mac can still reveal and read the folder. Revisit an encrypted-disk-image approach later if that threat matters.
# 6. Preview — All Formats
Handled by an async worker so uploads never block on processing:
| Type | Approach |
|---|---|
| JPEG/PNG/WebP | Direct thumbnail via libvips bindings (fast, low memory) |
| HEIC/HEIF (iPhone default) | libvips (has HEIF support via libheif) → convert to WebP thumb + JPEG preview |
| RAW (.dng, .cr2, etc.) | libvips or dcraw fallback → extract embedded JPEG preview if present (fast path), else decode |
| MOV/MP4/HEVC video | ffmpeg: extract frame at 1s for thumbnail, transcode a low-bitrate H.264 preview for browser playback (HEVC often won't play natively in browsers) |
| GIF | First frame as thumb, serve original for preview (already web-native) |
| Other/unknown | Generic file-type icon, offer direct download |

Flow: upload → status = processing → worker picks it up from a queue table (or just a status column poll) → generates thumbnail_path and preview_path → status = ready. Gallery UI shows a spinner/placeholder for anything still processing.
# 7. API Surface (v1)
| Endpoint | Method | Purpose |
|---|---|---|
| /devices/register | POST | First-time device registration, returns device_id + auth token |
| /files/exists | GET | Check if a hash already exists (pre-upload dedup check) |
| /upload | POST | Resumable (tus) file upload |
| /sync/diff | GET | List files this device hasn't synced yet |
| /sync/ack | POST | Mark file IDs as synced for this device |
| /media/:id/thumbnail | GET | Serve thumbnail |
| /media/:id/preview | GET | Serve preview (transcoded video / converted HEIC) |
| /media/:id/original | GET | Serve full original file (download) |
| /locked/unlock | POST | Re-auth with passphrase, returns short-lived locked-session token |
| /locked/files | GET | List locked files (requires locked-session token) |
| /locked/move | POST | Move a file in/out of the locked folder |

# 8. Build Order (Suggested Milestones)
| Phase | Deliverable |
|---|---|
| 1 | Go server: device registration, SQLite schema, basic /upload storing to /storage/blobs with hash-based naming |
| 2 | Sync protocol: /files/exists, /sync/diff, /sync/ack — test with 2 simulated devices via curl |
| 3 | Thumbnail/preview worker (libvips + ffmpeg), async queue |
| 4 | Web client: upload UI (drag/drop + mobile camera capture), gallery grid with lazy-loaded thumbnails |
| 5 | Locked folder: passphrase unlock flow, move-to-locked UI |
| 6 | Tailscale setup + auth token hardening, resumable upload (tus) for flaky mobile connections |
| 7 (later) | Native iOS/Android app with background camera-roll auto-upload |

# 9. Open Decisions For Later
Do you want locked-folder blobs encrypted at rest, or is app-layer access control enough for your threat model?
Retention/versioning: if a file is deleted from one device, does it disappear everywhere, or stay on the Mac as source of truth? (Recommend: Mac is always source of truth, deletes are per-device-view only unless explicitly "delete everywhere.")
Storage limits per device or total — worth a simple disk-usage dashboard early on.