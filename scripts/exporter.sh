#!/bin/sh
# Builds and starts the pinned real exporter for local E2E (#166).
#
# The exporter [timemore-dot-prometheus-exporter] is an immutable source
# snapshot at a single commit; no sibling checkout or deployed server is
# required. The build cache lives under ${XDG_CACHE_HOME:-$HOME/.cache};
# pinning stays the commit below, so the binary is reproducible from it.
#
# Usage:
#   scripts/exporter.sh build             build the pinned exporter binary
#   scripts/exporter.sh start <output-dir>  start it (env: EXPORTER_PORT, AUTH_TOKEN)
#
# Env:
#   EXPORTER_COMMIT   override the pinned commit (default below)
#
# [timemore-dot-prometheus-exporter]: https://github.com/MateuxLucax/timemore-dot-prometheus-exporter
set -eu

case "${1:-}" in
  build|start) ;;
  *) echo "usage: $0 build|start" >&2; exit 2 ;;
esac

repo_root=$(cd "$(dirname "$0")/.." && pwd)
exporter_commit="${EXPORTER_COMMIT:-1e637ba9000a7b71cf367d343ede22a13071aaf9}"
exporter_repo="https://github.com/MateuxLucax/timemore-dot-prometheus-exporter.git"
cache_root="${XDG_CACHE_HOME:-$HOME/.cache}/caparao-exporter"
build_bin="$cache_root/$exporter_commit/exporter"

ensure_source() {
  src="$cache_root/source/$exporter_commit"
  if [ -d "$src/.git" ] && [ "$(git -C "$src" rev-parse HEAD 2>/dev/null)" = "$exporter_commit" ]; then
    return 0
  fi
  mkdir -p "$(dirname "$src")"
  tmp="$src.tmp.$$"
  rm -rf "$tmp"
  git clone --quiet "$exporter_repo" "$tmp"
  git -C "$tmp" checkout --quiet "$exporter_commit"
  rm -rf "$src"
  mv "$tmp" "$src"
}

build() {
  mkdir -p "$cache_root/$exporter_commit"
  if [ -x "$build_bin" ]; then return 0; fi
  ensure_source
  (cd "$cache_root/source/$exporter_commit" && CGO_ENABLED=0 go build -trimpath -o "$build_bin" .)
}

start() {
  out_dir="${1:?start needs an output directory}"
  port="${EXPORTER_PORT:-8099}"
  build
  mkdir -p "$out_dir"
  # REMOTE_WRITE_URL is required by the exporter even though SQLite is the
  # source of truth: POST /api/v1/brews commits to SQLite first and answers
  # 2xx before any Prometheus sync. Point it at the status endpoint of a
  # discarded loopback address with a long sync interval so no sync is
  # attempted during a test; Prometheus remote-write coverage lives in the
  # exporter's own integration suite, not here (#166).
  AUTH_TOKEN="${AUTH_TOKEN:?start needs AUTH_TOKEN}" \
    LISTEN_ADDR="127.0.0.1:$port" \
    DB_PATH="$out_dir/caparao.db" \
    SYNC_INTERVAL=1h \
    REMOTE_WRITE_URL="http://127.0.0.1:19090/api/v1/write" \
    exec "$build_bin" >"$out_dir/server.log" 2>&1
}

# "$@": forward build/start; exit status is the command's status.
"$@"
