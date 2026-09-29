# Rebuild the hierarchy capture setup

This lab uses two historical development revisions because the `android.ui.hierarchy` producer and its Perfetto viewer are not part of a normal Compose or Perfetto release:

- Perfetto `dev/zezeozue/ui_hierarchy` (the trace schema, Java SDK AAR, processor module, and UI viewer must come from this same branch).
- AndroidX Gerrit change `refs/changes/66/4328066/1` (adds the Compose instrumentation hooks and `ui-tracing-perfetto` AAR).

This page records the actual setup used for the checked-in app and screenshots. Expect these old revisions to require their matching Gradle, Android SDK, and source trees. On the tested Linux setup, Java 17, Android SDK platforms 35 and 37.1, Build Tools 36.0.0, Perfetto host build dependencies, and the AndroidX checkout's Gradle 9.8.0-rc-1 wrapper were used. The AndroidX harness compiles Compose runtime, UI, animation, and foundation from that checkout; replacing these with current Maven Compose artifacts silently loses the hooks.

## 1. Get the source trees

Choose a workspace directory. The commands below use `$HOME/dev`; change it if you prefer another location. Run this block in your first shell. It creates the workspace and clones the lab at `compose-tracing-demo` with both source trees as siblings:

```sh
export WORKSPACE_DIR="$HOME/dev"
mkdir -p "$WORKSPACE_DIR"
cd "$WORKSPACE_DIR"
git clone https://github.com/fiveapplesonthetable/compose-trace-lab.git compose-tracing-demo
git clone https://github.com/google/perfetto.git perfetto-hierarchy
git -C perfetto-hierarchy checkout dev/zezeozue/ui_hierarchy
git -C perfetto-hierarchy fetch origin 7ef150035ea7b3e10674314a7c89789f64ea5a81
git -C perfetto-hierarchy checkout 7ef150035ea7b3e10674314a7c89789f64ea5a81
git clone https://android.googlesource.com/platform/frameworks/support androidx-hierarchy
git -C androidx-hierarchy fetch https://android.googlesource.com/platform/frameworks/support refs/changes/66/4328066/1
git -C androidx-hierarchy checkout 3a1d0ceab3e969fe0ab011628b96bfe902932040
```

These commands pin the source revisions used for the checked-in app and screenshots. The Perfetto development branch and AndroidX Gerrit patchset can move; the AndroidX commit is the fetched CL revision used here.

Install JDK 17, Python 3, Git, and the Android SDK command-line tools. If you use Android Studio, install **Android SDK Command-line Tools (latest)** and **Android SDK Platform-Tools** from **Tools → SDK Manager → SDK Tools**. Perfetto supplies its own Node.js/npm; do not install Node or pnpm separately. Set the SDK location and add its command-line and platform tools to your shell path before using `sdkmanager` or `adb` (adjust the path for your machine):

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --licenses
sdkmanager "platforms;android-35" "platforms;android-37.1" "build-tools;36.0.0" "platform-tools"
```

Platform 37.1 is the AndroidX harness and tracing library compile SDK. The Perfetto SDK AAR build script also reads `platforms/android-35/android.jar`, so Platform 35 is required even for the tracing build. Install the Perfetto host and UI dependencies, then build its tools:

```sh
cd perfetto-hierarchy
./tools/install-build-deps --android --ui
./tools/gn gen out/default
./tools/ninja -C out/default trace_processor_shell protoc
```

`--android --ui` installs the host build dependencies, Android NDK needed by the SDK AAR, and UI dependencies, including Perfetto's hermetic Node/npm. `./ui/run-dev-server` builds and serves the branch's UI at `http://127.0.0.1:10000`.

## 2. Prepare AndroidX and build the instrumented app

In a new shell, set `WORKSPACE_DIR` to the directory containing the three cloned repositories again (shell variables from the first shell do not carry over). The preparation script adds a small app harness and the CL module to the local AndroidX checkout, and adjusts that checkout's wrapper to its required Gradle version. These are local edits in the AndroidX clone, not edits to your public lab checkout.

```sh
WORKSPACE_DIR="$HOME/dev" # Change this to the directory containing the clones.
export LAB_DIR="$WORKSPACE_DIR/compose-tracing-demo"
cd "$LAB_DIR"
export ANDROID_HOME="$HOME/Android/Sdk" # change this to your SDK path
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
export ANDROIDX_DIR="$WORKSPACE_DIR/androidx-hierarchy"
export PERFETTO_DIR="$WORKSPACE_DIR/perfetto-hierarchy"
./scripts/prepare_androidx_checkout.sh
./scripts/build_hierarchy_app.sh
```

### What the AndroidX CL changes, and what the local harness adds

The CL is the Compose instrumentation patch. Fetching and checking out `refs/changes/66/4328066/1` checks out the CL's source changes; you do not need to manually copy individual Kotlin files or hand-edit `Composer.kt`. The commit message describes the instrumentation hooks by module:

- `compose:runtime:runtime` adds composition enter/exit, recomposition count/cause, and state mutation hooks.
- `compose:ui:ui` adds LayoutNode lifecycle, measure/layout and lookahead pass, and pointer/key dispatch hooks.
- `compose:animation:animation-core` adds tracing for `Animatable`, `Transition`, `InfiniteTransition`, and suspending animations.
- `compose:foundation:foundation` adds scroll-delta and lazy-layout prefetch hooks.
- `compose:ui:ui-tracing-perfetto` implements `UiHierarchyDataSource`, per-frame tree capture, delta compression, coordinate calculation, and event batching. The implementation is in `compose/ui/ui-tracing-perfetto/src/main/java/androidx/compose/ui/tracing/perfetto/` (`UiHierarchyTracing`, `UiHierarchyDataSource`, `Capturer`, `SnapshotEncoder`, and record/config helpers).

After checkout, verify the CL and its public registration API before building:

```sh
git -C "$ANDROIDX_DIR" log -1 --format='%H%n%s%n%b'
test -f "$ANDROIDX_DIR/compose/ui/ui-tracing-perfetto/src/main/java/androidx/compose/ui/tracing/perfetto/UiHierarchyTracing.kt"
rg -n 'fun install|const val NAME' \
  "$ANDROIDX_DIR/compose/ui/ui-tracing-perfetto/src/main/java/androidx/compose/ui/tracing/perfetto"
```

The app must compile against these checked-out `:compose:runtime:runtime`, `:compose:ui:ui`, `:compose:animation:animation-core`, and `:compose:foundation:foundation` projects. A Maven Compose dependency instead gives you an uninstrumented runtime and does not emit the hierarchy data.

`prepare_androidx_checkout.sh` supplies the build wiring around that CL. Its changes are intentionally small and local to the AndroidX clone:

1. Adds `includeProject(":compose:ui:ui-tracing-perfetto", [BuildType.COMPOSE])` and a `:trace-lab-app` project pointing at this repo's `androidx-harness/` directory in AndroidX `settings.gradle`.
2. Sets AndroidX `gradle/wrapper/gradle-wrapper.properties` to the Gradle 9.8.0-rc-1 wrapper required by this historical checkout.
3. Copies `androidx-harness/ui-tracing-perfetto.build.gradle` to `compose/ui/ui-tracing-perfetto/build.gradle`. That build file packages the CL capturer as an Android library and links it to the source-built Compose UI, animation, foundation, and locally built Perfetto SDK AAR.
4. Uses `androidx-harness/build.gradle` as the app module. It points its Kotlin/manifest/resources and instrumentation source sets at this repo's `app/` directory and depends on the AndroidX source projects. It also includes the generated tracing and data source AARs.

The script is safe to re-run: it removes any prior `:trace-lab-app` settings entry before adding the current relative project mapping, avoids duplicate include lines, writes the tracing module Gradle file, and sets the wrapper distribution URL. To review the exact local diff before building, use:

```sh
git -C "$ANDROIDX_DIR" status --short
git -C "$ANDROIDX_DIR" diff -- settings.gradle gradle/wrapper/gradle-wrapper.properties compose/ui/ui-tracing-perfetto/build.gradle
```

To apply the harness edits by hand instead, keep the existing `includeProject(":compose:ui:ui-lint", [BuildType.COMPOSE])` entry and add these project includes in AndroidX `settings.gradle` (the paths assume the three checkouts are siblings as in step 1):

```groovy
includeProject(":compose:ui:ui-tracing-perfetto", [BuildType.COMPOSE])
includeProject(":trace-lab-app", "../compose-tracing-demo/androidx-harness", [BuildType.COMPOSE])
```

Set `gradle/wrapper/gradle-wrapper.properties` to `distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.0-rc-1-bin.zip`, then copy `androidx-harness/ui-tracing-perfetto.build.gradle` to the CL module's `compose/ui/ui-tracing-perfetto/build.gradle`. `androidx-harness/build.gradle` is the `:trace-lab-app` build file. The checked-in preparation script is the canonical reproducible version of those edits and calculates the app path if your workspace differs. Do not commit these AndroidX-local harness edits to the AndroidX checkout when you only intend to build this demo.

The build script invokes Perfetto's `tools/build_java_sdk_aar` executable (this checkout has no `.py` suffix) to build `perfetto-datasource.aar`, builds `ui-tracing-perfetto.aar` from the CL checkout, then builds the debug app and instrumentation APK against the patched Compose sources. It places the generated AARs in the ignored `app/libs/` directory. After installation, the app's startup log should say `Perfetto UI hierarchy tracing initialized`.

The SDK AAR command defaults to `arm64-v8a`, matching the Pixel 4 devices used here. For another ABI, edit `--abis` in `scripts/build_hierarchy_app.sh` to include that device's ABI.

```sh
adb devices -l
cd "$ANDROIDX_DIR"
ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_HOME" \
  ANDROID_SERIAL=YOUR_DEVICE_SERIAL ALLOW_PUBLIC_REPOS=1 ALLOW_MISSING_PROJECTS=1 PROJECT_PREFIX=:trace-lab-app \
  ./gradlew -PcomposeTraceLabDir="$LAB_DIR" \
  :trace-lab-app:installDebug --no-daemon --dependency-verification=off
adb -s YOUR_DEVICE_SERIAL shell monkey -p dev.demo.uitracing 1
sleep 2
adb -s YOUR_DEVICE_SERIAL logcat -d -s TraceLab
```

Use the harness's `:trace-lab-app:installDebug` task, not the normal root `./gradlew installDebug` path: the latter is useful as a non-tracing fallback but compiles Compose from Maven and does not include the CL's Compose runtime hooks. The instrumentation journey is also run from the AndroidX harness:

```sh
cd "$ANDROIDX_DIR"
ANDROID_HOME="$ANDROID_HOME" ANDROID_SDK_ROOT="$ANDROID_HOME" \
  ANDROID_SERIAL=YOUR_DEVICE_SERIAL ALLOW_PUBLIC_REPOS=1 ALLOW_MISSING_PROJECTS=1 PROJECT_PREFIX=:trace-lab-app \
  ./gradlew -PcomposeTraceLabDir="$LAB_DIR" \
  :trace-lab-app:connectedDebugAndroidTest --no-daemon --dependency-verification=off
```

The journey covers all seven screens: repeated state changes, gallery filtering, lazy list/grid scrolling, form input, animation, dialog windows, AndroidView interop, and StateFlow/SharedFlow/cold Flow updates. The test checks visible outcomes; it is a workload and functional check, not a performance benchmark.

The journey passed on both attached Android 13 phones when run without a trace active; a Pixel 4 run also passed when Gradle targeted it alone with `ANDROID_SERIAL`. In one simultaneous `include_everything` capture and two-device E2E run, the Pixel 4 instrumentation ended at 10.019 seconds with an empty runner failure while the Pixel 4 XL passed. Treat instrumentation under the full hierarchy capture as an overhead stress case. For clean functional verification, finish the test first; for trace collection, perform the same actions manually or in a separate capture run.

## 3. Check device source support

Enable USB debugging, accept the device authorization prompt, then select one serial explicitly. The lab was exercised on Pixel 4 and Pixel 4 XL running Android 13/API 33. Verify that the app producer registered:

```sh
adb -s YOUR_DEVICE_SERIAL shell monkey -p dev.demo.uitracing 1
sleep 2 # Give Application.onCreate time to register the producer.
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

At `http://127.0.0.1:10000`, click **Open trace file** and choose the `.pftrace` file. Select **UI Hierarchy** in the left sidebar for the three-pane viewer. Use the snapshot scrubber to move through the capture; inspect the layout rectangles/3D stack, node tree and properties. Select the hierarchy track or a Compose event to jump between the viewer and exact timeline timestamp. The standard timeline remains useful for correlating hierarchy/state changes with a late frame and scheduled app work.

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

One diagnostic capture on Pixel 4 overlapped the instrumented E2E journey. Decoding it with the branch schema and querying it with the matching `trace_processor_shell` produced 126 UI snapshots across windows and 101 emitted/zero skipped frames for the app's main window. The event table included 8,950 composable calls, 3,319 scope events, 860 invalidations, 3,244 state reads, 1,716 writes, 1,878 state changes, 622 animation frames, and 21 scroll events. The recomposition-cause view attributed a changed integer state to `TraceLab` in `MainActivity.kt`. The Pixel 4 instrumentation did not pass in that overlapped run: it ended after 10.019 seconds with an empty runner failure. The Pixel 4 XL passed. The seven-screen screenshot tour was recorded separately with manual navigation. Use the no-trace E2E run described above as the functional pass; treat the overlapped capture as a trace-overhead stress case, not a passing E2E result.

That is useful for answering “which state changed, what scopes ran, and what UI tree/properties were present around this interaction?” The hierarchy view does not establish that a composable caused jank. Use the timeline, FrameTimeline, and scheduler tracks to ask whether a frame was late and what the app/system threads were doing. The counts also show why `include_everything` can be expensive: this is high-volume diagnostic tracing, not a low-overhead always-on profiler or a benchmark result.

## Troubleshooting

- **No hierarchy source in `perfetto --query`:** verify both AARs were built from the specified source revisions and included in the AndroidX harness APK; confirm the app startup log. A normal Maven Compose build does not contain the CL's runtime hooks.
- **`No field named ui_hierarchy_config`:** encode the textproto with `perfetto-hierarchy/out/default/protoc`, then use `scripts/capture_trace.sh`. The phone's text parser is too old for the branch schema.
- **A pushed config is permission-denied:** use the script's `cat config | perfetto -c -` pipe. The service's SELinux context cannot read arbitrary `/data/local/tmp` files directly.
- **Viewer says no hierarchy data:** open the trace in the UI from the matching Perfetto branch, not the stable hosted UI; verify trace includes packets and app producer was registered.
- **Gradle fails resolving AndroidX project dependencies:** rerun `prepare_androidx_checkout.sh` after verifying the exact CL checkout. Keep `ANDROIDX_DIR` and `PERFETTO_DIR` set to absolute paths.
- **Multiple devices:** pass `ANDROID_SERIAL` consistently to install, tests, and capture.
