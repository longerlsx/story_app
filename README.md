# Story App

Android TXT novel reader focused on local reading, external `.txt` import, and reliable reading-anchor restoration.

## Local Setup

Add these environment variables to your shell startup file:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

The existing local AVD is:

- `Pixel 8`
- `Android 14 / API 34`

Record the device actually used for each run; current evidence and temporary
emulator configurations are linked from [docs/current-state.md](docs/current-state.md).

## Agent Context

For project context, requirements, code, or documentation work, select the relevant
route from the documentation entry point:

- [docs/README.md](docs/README.md)
- [Current features, interactions, and requirements](docs/current-state.md)

Current state and operating guidance come first. Historical plans and matching
bug records are read when relevant, not as a mandatory sequence for every task.

## Build Notes

- The project is pinned to tool versions that are likely to hit the machine's existing Gradle and Android dependency caches first.
- The app currently uses `compileSdk = 36` and `targetSdk = 34`. This keeps AndroidX/Compose dependencies happy without changing runtime-target behavior yet.
- Instrumentation tests currently rely on a dependency-only fallback repo at `.local-maven/` for UTP artifacts on this machine. It is intentionally excluded from `pluginManagement` so Android Gradle Plugin resolution still comes from official repositories.
- During sandboxed command execution, Gradle may need a writable `GRADLE_USER_HOME`, but normal Android Studio and local terminal use should prefer the standard wrapper flow.
