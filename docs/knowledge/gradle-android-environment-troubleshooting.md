# Gradle And Android Environment Troubleshooting

This document captures the environment and dependency-resolution lessons learned while bootstrapping the Android reader project on this machine.

It is meant for future threads that need to answer questions such as:

- why Gradle sync fails on first open
- whether a global Gradle install is needed
- how to separate wrapper download problems from Maven dependency problems
- how to use the local fallback repository without polluting plugin resolution

## Stable Baseline

These are configured tool versions and the historical emulator test baseline,
not evidence that a device is connected or a fresh build has passed. Check the
current wrapper/build files and device list when those facts matter.

Current project baseline:

- Gradle wrapper: `9.3.1`
- Android Gradle Plugin: `9.1.0`
- Kotlin: `2.2.10`
- JDK: Android Studio embedded JBR
- Emulator baseline: `Pixel 8 / Android 14 / API 34`

Relevant files:

- [gradle/wrapper/gradle-wrapper.properties](/Users/longshengxi/proj/story_app/gradle/wrapper/gradle-wrapper.properties)
- [gradle/libs.versions.toml](/Users/longshengxi/proj/story_app/gradle/libs.versions.toml)
- [settings.gradle.kts](/Users/longshengxi/proj/story_app/settings.gradle.kts)
- [README.md](/Users/longshengxi/proj/story_app/README.md)

## Core Rules

### 1. Do not install global Gradle for this project

Use `./gradlew` only.

Reason:

- wrapper keeps the project on one known Gradle version
- global Gradle adds version drift without solving Android sync issues

### 2. Use Android Studio's embedded JDK

Preferred setup:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Reason:

- avoids maintaining a second Java installation
- matches what Android Studio itself expects

### 3. Treat plugin resolution and dependency resolution as different systems

Current configuration:

- `pluginManagement` uses only official repositories
- `dependencyResolutionManagement` may include a local fallback repository

Why this matters:

- Android Gradle Plugin and Kotlin plugin resolution should stay on official sources
- local fallback repos are acceptable for runtime/test dependencies on a specific machine
- mixing them together makes sync failures harder to diagnose

Current project rule in [settings.gradle.kts](/Users/longshengxi/proj/story_app/settings.gradle.kts):

- `google()`
- `mavenCentral()`
- `gradlePluginPortal()`

for plugins, and:

- `google()`
- `mavenCentral()`
- `.local-maven`

for normal dependencies

## Common Failure Classes

### Wrapper distribution download failure

Symptoms:

- first open stalls at downloading Gradle
- errors mention `gradle-*.zip`
- build never reaches dependency resolution

What it means:

- the Gradle distribution itself cannot be downloaded or locked into place

Do first:

1. verify proxy/network path
2. verify wrapper version is fixed and valid
3. avoid changing Maven repos, because this is not a Maven problem

### Maven dependency download failure

Symptoms:

- wrapper starts successfully
- failures mention artifacts from `google()` or `mavenCentral()`
- specific AndroidX/Compose/UTP coordinates cannot be resolved

What it means:

- Gradle is running, but one or more repositories/artifacts are unavailable

Do first:

1. check whether the dependency should come from official repos
2. use proxy if the problem is network-related
3. only use `.local-maven` as a narrow fallback for missing machine-specific artifacts

## Local Fallback Repository Policy

The project currently allows a `.local-maven` repository in dependency resolution only.

Use it only when:

- a machine-specific artifact is already cached locally
- official sources are correct in principle but unreliable in practice on the current machine
- the fallback is dependency-scoped, not plugin-scoped

Do not use it to:

- replace `google()` or `mavenCentral()`
- resolve AGP or Kotlin plugin versions
- mask a fundamentally wrong dependency coordinate

## Proxy Guidance

If downloads fail due to network conditions, prefer using the shell proxy path instead of rewriting repository configuration.

Typical approach on this machine:

```bash
export https_proxy=http://127.0.0.1:<port>
export http_proxy=http://127.0.0.1:<port>
export all_proxy=socks5://127.0.0.1:<port>
```

Reason:

- keeps build configuration clean
- avoids turning a network problem into a repository-resolution problem

Gradle runs downloads in a JVM and may not follow the shell proxy variables.
If curl through the verified local HTTP proxy succeeds while Java TLS fails,
pass the proxy to the Gradle JVM, including its daemon. For example (replace
the port with the currently verified listener):

```bash
./gradlew --no-daemon \
  '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttp.nonProxyHosts= -Dhttps.nonProxyHosts=' \
  --max-workers=2 testDebugUnitTest assembleDebug
```

This is a command-local network setting, not a repository or dependency change.
Verify the official Google and Maven Central URLs through the proxy separately;
one working host does not establish the other. Keep baseline and changed builds
on the same toolchain. Use fewer workers when the emulator and build compete for
memory; record software-rendering and host-pressure limits with performance data.

## Sandbox vs Local Machine

Some failures seen during agent execution are sandbox-specific, not workstation failures.

Examples:

- Gradle wrapper lock-file permission problems
- `adb` daemon startup being blocked inside sandboxed execution

Interpretation rule:

- if Android Studio or local terminal works, but agent sandbox commands fail, do not immediately treat it as a broken local Android environment

## Practical Checklist For Future Threads

When Android sync or Gradle setup fails, use this order:

1. confirm `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT`
2. confirm wrapper version and project-pinned tool versions
3. decide whether the failure is wrapper-download or dependency-download
4. keep plugin repositories official
5. use proxy before inventing custom repository workarounds
6. only use `.local-maven` as a dependency fallback
7. distinguish sandbox failures from real machine failures

## What Worked Well In This Project

- pinning Gradle, AGP, and Kotlin early
- using Android Studio's bundled JDK
- keeping `.local-maven` out of plugin resolution
- treating emulator/device setup separately from Gradle setup
- fixing test-state pollution independently from build-environment issues

## Anti-Patterns To Avoid

- installing global Gradle to fix project sync
- changing repository configuration before identifying whether the failure is wrapper or Maven related
- letting local fallback repos participate in plugin resolution
- assuming every agent-side build failure means the developer machine is broken

## Device, UI, And Real-Text Verification

Use device/UI evidence when the question depends on real interaction or Android
service behavior. Start with `adb devices -l`; inspect available AVDs before
assuming no emulator can be used. Wake/start the relevant device when necessary
for the task, and use `adb shell svc power stayon true` during long sessions if
sleep would interrupt an ordinary UI check. Do not force the screen or charging
state awake for screen-off/background survival checks; preserve the user's normal
power settings and record whether the device is physically charging.

Before treating an ignored phone tap as an input-permission failure, confirm the
foreground app and wake the screen. A dimmed screen may consume the first tap.
The reader toolbar also auto-hides after 3 seconds: dumping the hierarchy and
waiting for another agent/tool round trip can make its coordinates stale. Once
the controls and their bounds are observed, perform the short dependent tap
sequence on the device with enough time for the toolbar to render, then inspect
the resulting screen. Do not repeatedly tap an unverified or unrelated app.
When streaming `uiautomator dump /dev/tty`, use `adb shell -tt` to provide the
remote TTY; an empty result without it is not proof that the controls are missing.
Filter the target media card itself, rather than its notification-list ancestor,
before printing control details. After an audio recorder exits, re-read the play/
pause state before tapping because its route change can pause the app.

Use Computer Use when existing automation cannot adequately check an interaction.
Page animations, chapter transitions, gesture conflicts, auto-hide, flashing, and
TTS follow need dynamic observation when feasible. Screenshots are sufficient for
stable layout/color checks. Record the device and input; an emulator result does
not prove compatibility with Xiaomi or another vendor.

Local video tools previously available:

- `/opt/homebrew/bin/ffmpeg`
- `/opt/homebrew/bin/ffprobe`

Reusable scrcpy 4.1 is installed at
`/Users/longshengxi/.local/share/story-app-tools/scrcpy-4.1/bin/scrcpy`.
Set `ADB=/Users/longshengxi/Library/Android/sdk/platform-tools/adb` for this
command. The installation keeps its source archive, license and `BUILD.txt`;
it is a development dependency, not temporary test evidence. Recording needs
no Internet. Keep it when deleting one-off scripts, backups and recordings.

This Mac uses an unmodified upstream v4.1 client built against its existing
FFmpeg 8.1.2 and SDL 3.4.10, plus libusb 1.0.30. The current Homebrew 4.1_1
bottle requires FFmpeg 9 and did not start here; that unusable client was
removed. TUNA's Homebrew mirror supplied the verified official server, whose
SHA256 is `deacb991ed2509715160ffdc7907e47b4160eb30d1566217e9047fd5b8850cae`.
Use command-local mirror settings from the [TUNA instructions](https://mirrors.tuna.tsinghua.edu.cn/help/homebrew-bottles/)
when needed; do not change the user's global sources or upgrade unrelated
dependencies just to obtain this tool. After a tool or dependency change,
verify a short actual recording on the task emulator before reserving a phone
window; `--version` alone does not establish recording readiness.

For silent-phone audio checks, distinguish capture paths. On the tested Xiaomi,
scrcpy 4.1 `--audio-source=output` yielded silence at media volume zero, while
`--audio-source=playback --no-playback --no-video --no-control --no-window
--require-audio` captured actual narration without changing that volume. Calibrate
with one known preview before a long recording. A nonzero recording proves the
captured output, not physical speaker/headphone sound or voice quality; preserve
that evidence boundary. See the [official audio options](https://github.com/Genymobile/scrcpy/blob/v4.1/doc/audio.md).

Stopping that capture can itself change the phone's audio route. In the measured
Xiaomi run, scrcpy's AudioPolicy died, the route returned to speaker, and Android
sent `ACTION_AUDIO_BECOMING_NOISY`; the app correctly paused. Correlate those
existing system events before calling an end-of-capture pause a playback failure.
Keep capture-tool shutdown outside claims about autonomous background playback.

User-provided real novel corpus:

- `/Users/longshengxi/Downloads/小说测试集`

Read relevant samples and retain the file identity and minimal input needed to
explain a finding. Keep temporary recordings, probes, and compiled artifacts out
of the product tree; remove one-off diagnostics after recording the useful result.

Current validation status belongs in [current-state.md](../current-state.md),
not in this stable operations document.
