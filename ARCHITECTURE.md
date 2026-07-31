# PhotoVault Architecture

## Overview

PhotoVault is a self-hosted photo/video backup system.

Goals:

- automatic backups
- cross-device sync
- deduplicated storage
- secure private access
- fast browsing
- minimal resource usage

---

# High Level

```
Phone

Laptop

Browser

        │

        ▼

    Tailscale

        │

        ▼

Go HTTP Server

        │

        ├──────────────┐

        ▼              ▼

SQLite         Filesystem

        │

        ▼

Background Workers
```

---

# Components

## Authentication

Device Registration

↓

Bearer Token

↓

Middleware

↓

Authenticated Device Context

---

## Upload

Multipart Upload

↓

Temporary File

↓

SHA-256

↓

Blob Store

↓

Database

↓

Sync State

↓

Background Queue

---

## Sync

Client

↓

GET /sync/diff

↓

Metadata Only

↓

Download Missing Files

↓

POST /sync/ack

Device identity always comes from authenticated middleware. Sync-diff never accepts a client-supplied `device_id`.

---

## Storage

```
storage/

    blobs/

        by-device/

            <device-slug>/

                <yyyy>/<mm>/<hash>.<ext>

    derived/

        thumbnails/

        previews/

    .locked/

    vault.db
```

---

# Package Layout

cmd/

internal/

app/

authn/

devices/

files/

synchronization/

storage/

uploads/

httpapi/

database/

config/

---

# Future Workers

Thumbnail Worker

Preview Worker

Cleanup Worker

Health Worker

---

# Request Lifecycle

HTTP

↓

Middleware

↓

Validation

↓

Service

↓

Repository

↓

SQLite

↓

Response

---

# Design Principles

Simple

Reliable

Deterministic

Stateless

Streaming

Idempotent

Production-ready