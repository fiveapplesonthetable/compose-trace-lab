#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PERFETTO_DIR="${PERFETTO_DIR:-$(cd "$ROOT/.." && pwd)/perfetto-hierarchy}"
SERIAL="${ANDROID_SERIAL:-}"
ADB=(adb)
if [[ -n "$SERIAL" ]]; then ADB+=( -s "$SERIAL" ); fi
OUT="${1:-$ROOT/traces/compose-hierarchy.pftrace}"
PROTOC="$PERFETTO_DIR/out/default/protoc"
PROTO="$PERFETTO_DIR/protos/perfetto/config/trace_config.proto"
[[ -x "$PROTOC" ]] || { echo "Build Perfetto branch protoc first: $PROTOC" >&2; exit 1; }
[[ -s "$ROOT/app/libs/perfetto-datasource.aar" && -s "$ROOT/app/libs/ui-tracing-perfetto.aar" ]] || {
  echo "Build/install the hierarchy-enabled app first (see docs/SETUP.md)." >&2; exit 1;
}
command -v adb >/dev/null || { echo "adb is required" >&2; exit 1; }
"${ADB[@]}" get-state >/dev/null

CONFIG="$(mktemp)"
REMOTE_CONFIG="/data/local/tmp/compose_hierarchy_$$.pb"
REMOTE_TRACE="/data/misc/perfetto-traces/compose_hierarchy_$$.pftrace"
DURATION_MS="$(python3 - "$ROOT/trace_config_available_sources.textproto" <<'PY'
from pathlib import Path
import re, sys
m = re.search(r'^\s*duration_ms\s*:\s*(\d+)', Path(sys.argv[1]).read_text(), re.M)
if not m:
    raise SystemExit('trace config must specify duration_ms')
print(m.group(1))
PY
)"
WAIT_SECONDS=$(( (DURATION_MS + 999) / 1000 + 5 ))
cleanup() {
  rm -f "$CONFIG"
  "${ADB[@]}" shell rm -f "$REMOTE_CONFIG" "$REMOTE_TRACE" >/dev/null 2>&1 || true
}
trap cleanup EXIT

"$PROTOC" --encode=perfetto.protos.TraceConfig -I "$PERFETTO_DIR" "$PROTO" \
  < "$ROOT/trace_config_available_sources.textproto" > "$CONFIG"
"${ADB[@]}" push "$CONFIG" "$REMOTE_CONFIG" >/dev/null
"${ADB[@]}" shell am force-stop dev.demo.uitracing >/dev/null 2>&1 || true
"${ADB[@]}" shell monkey -p dev.demo.uitracing 1 >/dev/null
sleep 2
mkdir -p "$(dirname "$OUT")"
echo "Recording $(( (DURATION_MS + 999) / 1000 )) seconds on ${SERIAL:-the selected adb device}. Exercise the app now."
echo "The config is encoded with the matching Perfetto branch protoc, then piped into Android's Perfetto service."
"${ADB[@]}" shell "cat $REMOTE_CONFIG | perfetto -D -c - -o $REMOTE_TRACE"
# Android 13's non-PTY adb shell can disconnect from a foreground Perfetto
# client before it finishes. -D starts a detached capture; wait through its TTL.
sleep "$WAIT_SECONDS"
"${ADB[@]}" pull "$REMOTE_TRACE" "$OUT" >/dev/null
test -s "$OUT" || { echo "Trace output is empty; inspect Perfetto errors above." >&2; exit 1; }
echo "Trace written: $OUT"
echo "Open it with the matching branch UI: $PERFETTO_DIR/ui/run-dev-server"
