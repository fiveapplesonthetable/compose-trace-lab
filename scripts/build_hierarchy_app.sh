#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROIDX_DIR="${ANDROIDX_DIR:-$(cd "$ROOT/.." && pwd)/androidx-hierarchy}"
PERFETTO_DIR="${PERFETTO_DIR:-$(cd "$ROOT/.." && pwd)/perfetto-hierarchy}"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$ANDROID_HOME" ]] || { echo "Set ANDROID_HOME to the Android SDK directory." >&2; exit 1; }
[[ -x "$ANDROID_HOME/platform-tools/adb" ]] || { echo "Android SDK platform-tools/adb is missing." >&2; exit 1; }

"$ROOT/scripts/prepare_androidx_checkout.sh"

python3 "$PERFETTO_DIR/tools/build_java_sdk_aar.py" \
  --abis arm64-v8a \
  --aar-out "$ROOT/app/libs/perfetto-datasource.aar" \
  --android-jar "$ANDROID_HOME/platforms/android-35/android.jar"

cd "$ANDROIDX_DIR"
ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_HOME" \
ALLOW_PUBLIC_REPOS=1 ALLOW_MISSING_PROJECTS=1 \
PROJECT_PREFIX=:compose:ui:ui-tracing-perfetto \
./gradlew -PcomposeTraceLabDir="$ROOT" \
  :compose:ui:ui-tracing-perfetto:assembleRelease \
  --no-daemon --dependency-verification=off

BUILD_DIR="$(ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_HOME" \
  ALLOW_PUBLIC_REPOS=1 ALLOW_MISSING_PROJECTS=1 \
  PROJECT_PREFIX=:compose:ui:ui-tracing-perfetto \
  ./gradlew -q -PcomposeTraceLabDir="$ROOT" \
  :compose:ui:ui-tracing-perfetto:properties --no-daemon \
  --dependency-verification=off | sed -n 's/^buildDir: //p')"
TRACING_AAR="$BUILD_DIR/outputs/aar/ui-tracing-perfetto-release.aar"
[[ -s "$TRACING_AAR" ]] || { echo "Tracing AAR not found: $TRACING_AAR" >&2; exit 1; }
cp "$TRACING_AAR" "$ROOT/app/libs/ui-tracing-perfetto.aar"

ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_HOME" \
ALLOW_PUBLIC_REPOS=1 ALLOW_MISSING_PROJECTS=1 \
PROJECT_PREFIX=:trace-lab-app \
./gradlew -PcomposeTraceLabDir="$ROOT" \
  :trace-lab-app:assembleDebug :trace-lab-app:assembleAndroidTest \
  --no-daemon --dependency-verification=off

echo "Patched app APK: inspect the :trace-lab-app:assembleDebug Gradle output directory."
