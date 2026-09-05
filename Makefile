.PHONY: dev build install uninstall stop start logs test

# ── Development ───────────────────────────────────────────────────────────────

## Start the Go API server in dev mode (use pnpm dev --host separately for frontend)
dev:
	go run ./cmd/photovault/

## Start Vite dev server (run alongside `make dev`)
dev-web:
	cd mnemos-web && pnpm dev --host

# ── Production Build & Install ────────────────────────────────────────────────

## Build frontend + backend + install as a background launchd service (auto-starts on login)
install:
	./scripts/install.sh

## Remove the launchd service and binary
uninstall:
	./scripts/install.sh --uninstall

## Build the production binary (without installing)
build:
	@echo "→ Building frontend..."
	cd mnemos-web && pnpm run build
	@echo "→ Staging embedded assets..."
	rm -rf cmd/photovault/web/dist
	cp -r mnemos-web/dist cmd/photovault/web/dist
	@echo "→ Compiling binary..."
	go build -ldflags="-s -w" -o photovault ./cmd/photovault/
	@echo "✓ Binary → ./photovault"

# ── Service Control ───────────────────────────────────────────────────────────

## Stop the background service
stop:
	launchctl stop com.photovault.server

## Start the background service (if already installed)
start:
	launchctl start com.photovault.server

## Tail service logs
logs:
	tail -f ~/Library/Logs/PhotoVault/photovault.log

## Tail error logs
logs-err:
	tail -f ~/Library/Logs/PhotoVault/photovault-error.log

# ── Testing ───────────────────────────────────────────────────────────────────

## Run Go tests with race detector
test:
	go test -race ./...

## Run Go tests (no cache)
test-fresh:
	go test -race -count=1 ./...
