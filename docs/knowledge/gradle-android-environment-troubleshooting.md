# Gradle And Android Environment Troubleshooting

This document captures the environment and dependency-resolution lessons learned while bootstrapping the Android reader project on this machine.

It is meant for future threads that need to answer questions such as:

- why Gradle sync fails on first open
- whether a global Gradle install is needed
- how to separate wrapper download problems from Maven dependency problems
- how to use the local fallback repository without polluting plugin resolution

## Stable Baseline

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
