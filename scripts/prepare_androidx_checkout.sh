#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROIDX_DIR="${ANDROIDX_DIR:-$(cd "$ROOT/.." && pwd)/androidx-hierarchy}"
LAB_DIR="$ROOT"
SETTINGS="$ANDROIDX_DIR/settings.gradle"
WRAPPER="$ANDROIDX_DIR/gradle/wrapper/gradle-wrapper.properties"
MODULE_BUILD="$ANDROIDX_DIR/compose/ui/ui-tracing-perfetto/build.gradle"

[[ -d "$ANDROIDX_DIR/.git" ]] || { echo "AndroidX checkout not found: $ANDROIDX_DIR" >&2; exit 1; }
[[ -f "$ANDROIDX_DIR/compose/ui/ui-tracing-perfetto/src/main/java/androidx/compose/ui/tracing/perfetto/UiHierarchyTracing.kt" ]] || {
  echo "The AndroidX checkout is not on CL 4328066. Fetch refs/changes/66/4328066/1 first." >&2
  exit 1
}

python3 - "$SETTINGS" "$LAB_DIR" "$ANDROIDX_DIR" <<'PY'
from pathlib import Path
import os
import sys

path = Path(sys.argv[1])
lab = Path(sys.argv[2]).resolve()
androidx = Path(sys.argv[3]).resolve()
app_project = os.path.relpath(lab / 'androidx-harness', androidx).replace(os.sep, '/')
text = path.read_text()
text = '\n'.join(
    line for line in text.splitlines()
    if 'includeProject(":trace-lab-app"' not in line
)
entries = [
    'includeProject(":compose:ui:ui-tracing-perfetto", [BuildType.COMPOSE])',
    f'includeProject(":trace-lab-app", "{app_project}", [BuildType.COMPOSE])',
]
anchor = 'includeProject(":compose:ui:ui-lint", [BuildType.COMPOSE])'
for entry in entries:
    if entry not in text:
        if entry.startswith('includeProject(":compose:ui:ui-tracing-perfetto"'):
            text = text.replace(anchor, entry + '\n' + anchor, 1)
        else:
            text = text.replace(anchor, entry + '\n' + anchor, 1)
path.write_text(text)
PY

python3 - "$WRAPPER" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
lines = text.splitlines()
lines = [
    'distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.0-rc-1-bin.zip'
    if line.startswith('distributionUrl=') else line
    for line in lines
]
text = '\n'.join(lines) + '\n'
path.write_text(text)
PY

mkdir -p "$(dirname "$MODULE_BUILD")"
cp "$LAB_DIR/androidx-harness/ui-tracing-perfetto.build.gradle" "$MODULE_BUILD"
echo "Prepared AndroidX checkout: $ANDROIDX_DIR"
echo "App source and local AARs: $LAB_DIR/app"
echo "Use -PcomposeTraceLabDir=$LAB_DIR when building :trace-lab-app."
