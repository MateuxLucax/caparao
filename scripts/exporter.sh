#!/bin/sh
# Builds and starts the pinned real exporter for local E2E (#166).
#
# The exporter [timemore-dot-prometheus-exporter] is fetched as an
# immutable module version through `go install <module>@<commit>`; no
# sibling checkout, git clone, or deployed server is required. The
# binary lands under ${XDG_CACHE_HOME:-$HOME/.cache}; pinning stays the
# commit below, so the binary is reproducible from it.
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

exporter_commit="${EXPORTER_COMMIT:-1e637ba9000a7b71cf367d343ede22a13071aaf9}"
exporter_module="github.com/MateuxLucax/timemore-dot-prometheus-exporter"
cache_root="${XDG_CACHE_HOME:-$HOME/.cache}/caparao-exporter"
build_bin="$cache_root/$exporter_commit/exporter"

build() {
  mkdir -p "$cache_root/$exporter_commit"
  if [ -x "$build_bin" ]; then return 0; fi
  tmp="$build_bin.tmp.$$"
  rm -f "$tmp"
  GOBIN="$(dirname "$tmp")" CGO_ENABLED=0 go install "$exporter_module@$exporter_commit"
  mv "$cache_root/$exporter_commit/timemore-dot-prometheus-exporter" "$tmp" 2>/dev/null || true
  if [ -x "$tmp" ]; then mv "$tmp" "$build_bin"; else echo "exporter.sh: go install produced no binary at $tmp" >&2; exit 1; fi
}

start() {
  out_dir="${1:?start needs an output directory}"
  port="${EXPORTER_PORT:-8099}"
  build
  mkdir -p "$out_dir"
  # REMOTE_WRITE_URL is required by the exporter even though SQLite is the
  # source of truth: POST /api/v1/brews commits to SQLite first and answers
  # 2xx before any Prometheus sync. Point it at a discarded loopback address
  # with a long sync interval so no sync is attempted during a test;
  # Prometheus remote-write coverage lives in the exporter's own
  # integration suite, not here (#166).
  AUTH_TOKEN="${AUTH_TOKEN:?start needs AUTH_TOKEN}" \
    LISTEN_ADDR="127.0.0.1:$port" \
    DB_PATH="$out_dir/caparao.db" \
    SYNC_INTERVAL=1h \
    REMOTE_WRITE_URL="http://127.0.0.1:9/api/v1/write" \
    exec "$build_bin" >"$out_dir/server.log" 2>&1
}

# "$@": forward build/start; exit status is the command's status.
"$@"
