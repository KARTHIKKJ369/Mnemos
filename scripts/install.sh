#!/usr/bin/env bash
# PhotoVault install script for macOS.
# Builds the frontend + backend, installs the binary, and registers a
# launchd agent so the server auto-starts on login and restarts on crash.
#
# Usage:
#   ./scripts/install.sh          # install / upgrade
#   ./scripts/install.sh --uninstall   # stop and remove the service

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BINARY_NAME="photovault"
INSTALL_DIR="$HOME/bin"
LABEL="com.photovault.server"
PLIST_PATH="$HOME/Library/LaunchAgents/${LABEL}.plist"
BINARY_PATH="${INSTALL_DIR}/${BINARY_NAME}"
LOG_DIR="$HOME/Library/Logs/PhotoVault"

# ── Colour helpers ────────────────────────────────────────────────────────────
green()  { echo "  ✓ $*"; }
blue()   { echo "  → $*"; }
red()    { echo "  ✗ $*" >&2; }

# ── Uninstall ─────────────────────────────────────────────────────────────────
if [[ "${1:-}" == "--uninstall" ]]; then
  blue "Stopping PhotoVault service..."
  launchctl bootout "gui/$(id -u)/${LABEL}" 2>/dev/null || true
  rm -f "${PLIST_PATH}"
  rm -f "${BINARY_PATH}"
  green "PhotoVault uninstalled."
  exit 0
fi

# ── Dependency checks ─────────────────────────────────────────────────────────
for cmd in go node pnpm; do
  if ! command -v "$cmd" &>/dev/null; then
    red "$cmd is required but not found in PATH."
    exit 1
  fi
done

echo ""
echo "┌──────────────────────────────────────────────┐"
echo "│           PhotoVault Installer               │"
echo "└──────────────────────────────────────────────┘"
echo ""

# ── 1. Build React frontend ───────────────────────────────────────────────────
blue "Building React frontend..."
(cd "${REPO_ROOT}/mnemos-web" && pnpm install --frozen-lockfile --silent && pnpm run build)
green "Frontend built → mnemos-web/dist"

# ── 2. Copy dist into embed staging directory ─────────────────────────────────
EMBED_DIR="${REPO_ROOT}/cmd/photovault/web/dist"
blue "Staging frontend for embedding..."
rm -rf "${EMBED_DIR}"
cp -r "${REPO_ROOT}/mnemos-web/dist" "${EMBED_DIR}"
green "Staged → cmd/photovault/web/dist"

# ── 3. Build Go binary ────────────────────────────────────────────────────────
blue "Compiling Go binary (this embeds the frontend)..."
go build \
  -ldflags="-s -w" \
  -o "${REPO_ROOT}/photovault" \
  "${REPO_ROOT}/cmd/photovault/"
green "Binary built → ${REPO_ROOT}/photovault"

# ── 4. Install binary ─────────────────────────────────────────────────────────
blue "Installing binary to ${BINARY_PATH}..."
mkdir -p "${INSTALL_DIR}"
cp "${REPO_ROOT}/photovault" "${BINARY_PATH}"
chmod +x "${BINARY_PATH}"
green "Installed → ${BINARY_PATH}"

# ── 5. Create log directory ───────────────────────────────────────────────────
mkdir -p "${LOG_DIR}"

# ── 6. Write launchd plist ────────────────────────────────────────────────────
blue "Writing launchd plist → ${PLIST_PATH}..."

# Load existing PHOTOVAULT_STORAGE_PATH and PHOTOVAULT_HTTP_ADDRESS from .env
STORAGE_PATH=""
HTTP_ADDRESS="0.0.0.0:8080"
ENV_FILE="${REPO_ROOT}/.env"
if [[ -f "${ENV_FILE}" ]]; then
  while IFS='=' read -r key value; do
    # Strip surrounding quotes
    value="${value%\"}"
    value="${value#\"}"
    case "$key" in
      PHOTOVAULT_STORAGE_PATH) STORAGE_PATH="$value" ;;
      PHOTOVAULT_HTTP_ADDRESS)  HTTP_ADDRESS="$value"  ;;
    esac
  done < <(grep -v '^#' "${ENV_FILE}" | grep '=')
fi

cat > "${PLIST_PATH}" << PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
  "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${LABEL}</string>

  <key>ProgramArguments</key>
  <array>
    <string>${BINARY_PATH}</string>
  </array>

  <!-- Environment: picks up .env automatically; these are overrides -->
  <key>EnvironmentVariables</key>
  <dict>
$(if [[ -n "${STORAGE_PATH}" ]]; then
  echo "    <key>PHOTOVAULT_STORAGE_PATH</key>"
  echo "    <string>${STORAGE_PATH}</string>"
fi)
    <key>PHOTOVAULT_HTTP_ADDRESS</key>
    <string>${HTTP_ADDRESS}</string>
  </dict>

  <!-- Run from the repo root so .env is auto-loaded -->
  <key>WorkingDirectory</key>
  <string>${REPO_ROOT}</string>

  <!-- Restart automatically if it crashes -->
  <key>KeepAlive</key>
  <true/>

  <!-- Start on user login -->
  <key>RunAtLoad</key>
  <true/>

  <!-- Throttle rapid restarts (wait 5 s before retry) -->
  <key>ThrottleInterval</key>
  <integer>5</integer>

  <!-- Logs -->
  <key>StandardOutPath</key>
  <string>${LOG_DIR}/photovault.log</string>
  <key>StandardErrorPath</key>
  <string>${LOG_DIR}/photovault-error.log</string>
</dict>
</plist>
PLIST

green "Plist written."

# ── 7. Load / reload the service ─────────────────────────────────────────────
blue "Registering service with launchd..."
# Unload previous version if running (try plist path, service target, and kill if needed)
launchctl bootout "gui/$(id -u)" "${PLIST_PATH}" 2>/dev/null || launchctl bootout "gui/$(id -u)/${LABEL}" 2>/dev/null || true
sleep 0.5
launchctl bootstrap "gui/$(id -u)" "${PLIST_PATH}"
green "Service registered."

# Verify it started
sleep 1
if launchctl list | grep -q "${LABEL}"; then
  green "PhotoVault is running."
else
  red "Service registered but may not be running yet — check logs:"
  echo "     ${LOG_DIR}/photovault.log"
  echo "     ${LOG_DIR}/photovault-error.log"
fi

echo ""
echo "┌──────────────────────────────────────────────┐"
echo "│  PhotoVault is installed & running           │"
echo "│                                              │"
echo "│  URL  → http://$(hostname -s):${HTTP_ADDRESS##*:}         │"
echo "│  Logs → ~/Library/Logs/PhotoVault/           │"
echo "│                                              │"
echo "│  Commands:                                   │"
echo "│  Stop   → launchctl stop ${LABEL}  │"
echo "│  Start  → launchctl start ${LABEL} │"
echo "│  Remove → ./scripts/install.sh --uninstall   │"
echo "│  Upgrade→ ./scripts/install.sh               │"
echo "└──────────────────────────────────────────────┘"
echo ""
