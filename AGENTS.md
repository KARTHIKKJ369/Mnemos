# PhotoVault AI Engineering Guide

## Project

PhotoVault is a self-hosted photo/video backup and synchronization system.

The philosophy is:

- Reliability first
- Simplicity over cleverness
- Fast synchronization
- Deduplicated storage
- Production quality
- Minimal dependencies
- Long-term maintainability

This is NOT a prototype.

Every feature should be production-ready.

---

# Technology Stack

Language

- Go 1.24+

HTTP

- Chi Router

Database

- SQLite

Logging

- slog

Authentication

- Device Tokens

Storage

- Local Filesystem

Deployment

- Docker
- Tailscale

---

# Architecture

Request

↓

HTTP Handler

↓

Service

↓

Repository

↓

SQLite

Never skip layers.

Repositories never know HTTP.

Handlers never know SQL.

Services own business logic.

---

# Coding Standards

Use:

- context.Context everywhere
- wrapped errors
- dependency injection
- transactions where needed
- small interfaces
- composition

Avoid:

- globals
- hidden state
- unnecessary abstractions
- ORMs

---

# Database

Every schema change uses SQL migrations.

Never create schema in Go.

Every query should have a reason.

Every new index must be justified.

Use prepared statements for hot paths.

---

# Authentication

Device Tokens

- 32 random bytes
- base64.RawURLEncoding
- SHA-256 storage
- Constant-time verification

Never store plaintext tokens.

Never log secrets.

---

# Upload Pipeline

Uploads must:

stream

↓

temporary file

↓

SHA-256 while streaming

↓

atomic rename

↓

database transaction

↓

background processing

Never buffer entire files.

---

# Sync

Synchronization must:

- never duplicate blobs
- never re-download owned files
- be deterministic
- scale to 100k+ files

Avoid N+1 queries.

---

# API

Consistent JSON.

Errors:

{
    "error": {
        "code": "...",
        "message": "..."
    }
}

Never leak:

- filesystem paths
- SQL errors
- stack traces

---

# Concurrency

Assume:

- concurrent uploads
- concurrent sync
- concurrent downloads

Protect shared state.

Run race detector frequently.

---

# Performance

Always prefer:

streaming

prepared statements

indexes

zero-copy when practical

Avoid allocations.

---

# Testing

Every feature requires:

- unit tests
- race detector
- manual curl examples

Never merge untested code.

---

# Documentation

Whenever an endpoint changes:

Update README.

Explain:

- request
- response
- errors
- examples

---

# Review Checklist

Before every commit verify:

✓ security

✓ performance

✓ concurrency

✓ tests

✓ documentation

✓ backwards compatibility

---

# Behavior

Before writing code:

Explain

- architecture
- tradeoffs
- edge cases

Then implement.

Never generate placeholder code.

Never generate TODOs.

Never silently change architecture.

If unsure, ask first.