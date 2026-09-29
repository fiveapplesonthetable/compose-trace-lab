# Rebuild the hierarchy capture setup

This lab uses two historical development revisions because the `android.ui.hierarchy` producer and its Perfetto viewer are not part of a normal Compose or Perfetto release:

- Perfetto `dev/zezeozue/ui_hierarchy` (the trace schema, Java SDK AAR, processor module, and UI viewer must come from this same branch).
- AndroidX Gerrit change `refs/changes/66/4328066/1` (adds the Compose instrumentation hooks and `ui-tracing-perfetto` AAR).

This page records the actual setup used for the checked-in app and screenshots. Expect these old revisions to require their matching Gradle, Android SDK, and source trees. On the tested Linux setup, Java 17, Android SDK platform 35/build tools 36, Perfetto host build dependencies, and the AndroidX checkout's Gradle 9.8.0-rc-1 wrapper were used. The AndroidX harness compiles Compose runtime, UI, animation, and foundation from that checkout; replacing these with current Maven Compose artifacts silently loses the hooks.

## 1. Get the source trees

Choose a workspace directory. The commands below assume the lab is at `compose-tracing-demo` and both source trees are its siblings:

```sh
git clone https://github.com/fiveapplesonthetable/compose-trace-lab.git compose-tracing-demo
git clone https://github.com/google/perfetto.git perfetto-hierarchy
git -C perfetto-hierarchy checkout dev/zezeozue/ui_hierarchy
git clone https://android.googlesource.com/platform/frameworks/support androidx-hierarchy
git -C androidx-hierarchy fetch https://android.googlesource.com/platform/frameworks/support refs/changes/66/4328066/1
git -C androidx-hierarchy checkout FETCH_HEAD
```

For this checkout, the tested source revisions were Perfetto `7ef15003` and AndroidX `3a1d0ceab3e969fe0ab011628b96bfe902932040`. Record the actual hashes after checkout; Gerrit patchsets and development branches can move.

Install JDK 17, Python 3, Node/pnpm (Perfetto UI), Git, Ninja, and the Android SDK command-line tools. Set `ANDROID_HOME` (or `ANDROID_SDK_ROOT`) to the SDK directory. Install Android SDK Platform 35, Build Tools 36.0.0, and platform-tools. Install the Perfetto host dependencies and build its tools:

```sh
cd perfetto-hierarchy
./tools/install-build-deps
./tools/install-build-deps --ui
./tools/gn gen out/default
./tools/ninja -C out/default trace_processor_shell protoc
```

`--ui` installs the UI dependencies; `./ui/run-dev-server` builds and serves the branch's UI at `http://127.0.0.1:10000`.

## 2. Prepare AndroidX and build the instrumented app

Return to the lab directory. The preparation script adds a small app harness and the CL module to the local AndroidX checkout, and adjusts that checkout's wrapper to its required Gradle version. These are local edits in the AndroidX clone, not edits to your public lab checkout.

```sh
cd ../compose-tracing-demo
export LAB_DIR="$(pwd)"
export ANDROID_HOME="$HOME/Android/Sdk" # change this to your SDK path
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROIDX_DIR="$(cd ../androidx-hierarchy && pwd)"
export PERFETTO_DIR="$(cd ../perfetto-hierarchy && pwd)"
./scripts/prepare_androidx_checkout.sh
./scripts/build_hierarchy_app.sh
```

The build script builds `perfetto-datasource.aar` from the Perfetto branch, builds `ui-tracing-perfetto.aar` from the CL checkout, then builds the debug app and instrumentation APK against the patched Compose sources. It places the generated AARs in the ignored `app/libs/` directory. After installation, the app's startup log should say `Perfetto UI hierarchy tracing initialized`.

The SDK AAR command defaults to `arm64-v8a`, matching the Pixel 4 devices used here. For another ABI, edit `--abis` in `scripts/build_hierarchy_app.sh` to include that device's ABI.

```sh
adb devices -l
cd "$ANDROIDX_DIR"
ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_HOME" \
  ANDROID_SERIAL=YOUR_DEVICE_SERIAL ALLOW_PUBLIC_REPOS=1 ALLOW_MISSING_PROJECTS=1 PROJECT_PREFIX=:trace-lab-app \
  ./gradlew -PcomposeTraceLabDir="$(cd ../compose-tracing-demo && pwd)" \
  :trace-lab-app:installDebug --no-daemon --dependency-verification=off
adb -s YOUR_DEVICE_SERIAL logcat -d -s TraceLab
```

Use the harness's `:trace-lab-app:installDebug` task, not the normal root `./gradlew installDebug` path: the latter is useful as a non-tracing fallback but compiles Compose from Maven and does not include the CL's Compose runtime hooks. The instrumentation journey is also run from the AndroidX harness:

```sh
cd "$ANDROIDX_DIR"
ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_HOME" \
  ANDROID_SERIAL=YOUR_DEVICE_SERIAL ALLOW_PUBLIC_REPOS=1 ALLOW_MISSING_PROJECTS=1 PROJECT_PREFIX=:trace-lab-app \
  ./gradlew -PcomposeTraceLabDir="$(cd ../compose-tracing-demo && pwd)" \
  :trace-lab-app:connectedDebugAndroidTest --no-daemon --dependency-verification=off
```

The journey covers all seven screens: repeated state changes, gallery filtering, lazy list/grid scrolling, form input, animation, dialog windows, AndroidView interop, and StateFlow/SharedFlow/cold Flow updates. The test checks visible outcomes; it is a workload and functional check, not a performance benchmark.

The journey passed on both attached Android 13 phones when run without a trace active; a Pixel 4 run also passed when Gradle targeted it alone with `ANDROID_SERIAL`. In one simultaneous `include_everything` capture and two-device E2E run, the Pixel 4 instrumentation ended at 10.019 seconds with an empty runner failure while the Pixel 4 XL passed. Treat instrumentation under the full hierarchy capture as an overhead stress case. For clean functional verification, finish the test first; for trace collection, perform the same actions manually or in a separate capture run.

## 3. Check device source support

Enable USB debugging, accept the device authorization prompt, then select one serial explicitly. The lab was exercised on Pixel 4 and Pixel 4 XL running Android 13/API 33. Verify that the app producer registered:

```sh
adb -s YOUR_DEVICE_SERIAL shell perfetto --query
adb -s YOUR_DEVICE_SERIAL logcat -d -s TraceLab
```

Look for `android.ui.hierarchy` in the source query and the initialization log. Android 13's system Perfetto text config parser is older than this branch's schema, so `perfetto --txt` rejects the new `ui_hierarchy_config`. The capture script encodes the config with the branch's `protoc` and pipes the binary config into the device service; do not switch it to `--txt` or pass the pushed config path directly to the sandboxed service.

## 4. Capture and inspect

The default config in `trace_config_available_sources.textproto` enables every hierarchy category exposed by this CL, plus ftrace scheduling/ATrace, FrameTimeline, and process/system statistics. Run a 60-second interactive capture; navigate through the app while it records:

```sh
ANDROID_SERIAL=YOUR_DEVICE_SERIAL ./scripts/capture_trace.sh
```

The output defaults to `traces/compose-hierarchy.pftrace` (ignored by Git because traces can contain app/device details). Pass a different output path as the first argument if needed. For a shorter/focused recording, lower `duration_ms` in the textproto and keep enough time to perform the action. A broad 60-second capture on this device was 37 MB; keep captures short while iterating.

Start the matching local UI from the Perfetto checkout, then open the trace file:

```sh
cd "$PERFETTO_DIR"
./ui/run-dev-server
```

At `http://127.0.0.1:10000`, open the trace. Select **UI Hierarchy** in the left sidebar for the three-pane viewer. Use the snapshot scrubber to move through the capture; inspect the layout rectangles/3D stack, node tree and properties. Select the hierarchy track or a Compose event to jump between the viewer and exact timeline timestamp. The standard timeline remains useful for correlating hierarchy/state changes with a late frame and scheduled app work.

To inspect the same data in SQL, open **Query (SQL)** in that local UI and run:

```sql
INCLUDE PERFETTO MODULE android.ui_hierarchy;

SELECT count(*) AS snapshots
FROM android_ui_hierarchy_snapshot;

SELECT type, count(*) AS events
FROM android_ui_hierarchy_compose_event
GROUP BY type
ORDER BY events DESC;

SELECT ts, name, state_type, state_value
FROM android_ui_hierarchy_recomposition_cause
WHERE state_value IS NOT NULL
ORDER BY ts;

SELECT *
FROM android_ui_hierarchy_capture_completeness;
```

## What a real capture proved here

The validation capture was recorded from the hierarchy-enabled app on Pixel 4 while its E2E interaction journey ran. Decoding it with the branch schema and querying it with the matching `trace_processor_shell` produced 126 UI snapshots across windows and 101 emitted/zero skipped frames for the app's main window. The event table included 8,950 composable calls, 3,319 scope events, 860 invalidations, 3,244 state reads, 1,716 writes, 1,878 state changes, 622 animation frames, and 21 scroll events. The recomposition-cause view attributed a changed integer state to `TraceLab` in `MainActivity.kt`.

That is useful for answering “which state changed, what scopes ran, and what UI tree/properties were present around this interaction?” The hierarchy view does not establish that a composable caused jank. Use the timeline, FrameTimeline, and scheduler tracks to ask whether a frame was late and what the app/system threads were doing. The counts also show why `include_everything` can be expensive: this is high-volume diagnostic tracing, not a low-overhead always-on profiler or a benchmark result.

## Troubleshooting

- **No hierarchy source in `perfetto --query`:** verify both AARs were built from the specified source revisions and included in the AndroidX harness APK; confirm the app startup log. A normal Maven Compose build does not contain the CL's runtime hooks.
- **`No field named ui_hierarchy_config`:** encode the textproto with `perfetto-hierarchy/out/default/protoc`, then use `scripts/capture_trace.sh`. The phone's text parser is too old for the branch schema.
- **A pushed config is permission-denied:** use the script's `cat config | perfetto -c -` pipe. The service's SELinux context cannot read arbitrary `/data/local/tmp` files directly.
- **Viewer says no hierarchy data:** open the trace in the UI from the matching Perfetto branch, not the stable hosted UI; verify trace includes packets and app producer was registered.
- **Gradle fails resolving AndroidX project dependencies:** rerun `prepare_androidx_checkout.sh` after verifying the exact CL checkout. Keep `ANDROIDX_DIR` and `PERFETTO_DIR` set to absolute paths.
- **Multiple devices:** pass `ANDROID_SERIAL` consistently to install, tests, and capture.
