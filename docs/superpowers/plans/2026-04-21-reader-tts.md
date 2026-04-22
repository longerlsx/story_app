# Reader Background TTS Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add background-capable reader TTS with clear pause-vs-stop semantics, foreground-service playback, notification controls, short-sentence playback, long-press restart, auto-follow, highlight, and persistent TTS settings.

**Architecture:** Keep `ReaderScreen` as the orchestration point for reader-visible state, but move all long-lived playback concerns into a dedicated TTS service layer. Use short-sentence segmentation plus character-range bookkeeping so UI follow/highlight, pause/resume, stop/restart, and long-press restart all share one coordinate system. Persist only stable TTS preferences in `ReaderSettingsStore`; keep live playback session state in memory inside the service/controller layer, and make `ReaderScreen` hand the controller explicit start/stop inputs rather than raw UI state. Treat explicit chapter navigation and cross-book opening as stop boundaries, while ordinary manual scroll/page-turn gestures only suppress auto-follow for `10` seconds.

**Tech Stack:** Kotlin, Android `TextToSpeech`, foreground services, notifications, audio focus, Jetpack Compose, Coroutines/Flow, JUnit, Android instrumented tests

---

## File Map

- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/longerlsx/storyapp/StoryApplication.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/MainActivity.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/app/StoryApp.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/app/StoryAppState.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStore.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeMode.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducer.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderTtsSettings.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheet.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSegment.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSegmenter.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSessionState.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsPauseReason.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsPlaybackSnapshot.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsFollowSuppressionPolicy.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsStartRequest.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsController.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsService.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsNotificationFactory.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsAudioFocusManager.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsEngine.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/AndroidReaderTtsEngine.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsIntentFactory.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStoreTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSegmenterTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSessionStateTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsControllerTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsAudioFocusManagerTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsFollowSuppressionPolicyTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocatorTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducerTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsControlsTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsBackgroundPlaybackTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderUiDeviceHelpers.kt`

## Task 1: Persist TTS Preferences In Reader Settings

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderTtsSettings.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStore.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStoreTest.kt`

- [ ] **Step 1: Add failing unit tests for TTS defaults and persistence**

Run: `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest'`
Expected: FAIL on missing TTS fields such as voice name, rate, pitch, and timer preset, and on missing assertions that fresh playback sessions reuse the last saved TTS settings instead of reverting to first-run defaults.

- [ ] **Step 2: Add a focused TTS settings model**

Store:

- selected voice name
- speech rate
- pitch
- timer preset minutes or explicit `no timer`

Keep session-only fields such as remaining time and current spoken range out of persisted settings, but make persisted TTS settings the source of truth for every newly started playback session.

- [ ] **Step 3: Extend reader settings persistence without breaking existing settings**

Handle:

- fresh installs
- existing settings files without TTS keys
- `no timer` as a first-class persisted preference
- invalid saved voice names falling back later at runtime instead of corrupting persistence

- [ ] **Step 4: Re-run the TTS settings unit tests**

Run: `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest'`
Expected: PASS

- [ ] **Step 5: Commit the settings slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/core/model/ReaderTtsSettings.kt \
  app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt \
  app/src/main/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStore.kt \
  app/src/test/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStoreTest.kt
git commit -m "feat: persist reader tts settings"
```

## Task 2: Build Short-Sentence Segmentation And Playback State Models

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSegment.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSegmenter.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsPauseReason.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSessionState.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsPlaybackSnapshot.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsFollowSuppressionPolicy.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsStartRequest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSegmenterTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSessionStateTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsFollowSuppressionPolicyTest.kt`

- [ ] **Step 1: Write failing unit tests for segmentation, pause semantics, and follow suppression**

Cover:

- sentence-ending punctuation splits
- comma and semicolon secondary splits for overlong content
- no word-level queue explosion
- pause keeps remaining timer unchanged
- resume starts from the next recoverable character or range
- stop ends the current session and requires a later fresh start request
- manual follow suppression expires after `10` seconds of inactivity

Run: `./gradlew testDebugUnitTest --tests '*ReaderTtsSegmenterTest' --tests '*ReaderTtsSessionStateTest' --tests '*ReaderTtsFollowSuppressionPolicyTest'`
Expected: FAIL on missing models and reducers.

- [ ] **Step 2: Implement short-sentence segmentation with original character offsets**

Each segment must retain:

- `chapterIndex`
- `startCharOffset`
- `endCharOffset`
- `spokenText`

Keep segment construction deterministic so unit tests can lock expected offsets.

- [ ] **Step 3: Implement playback session and pause-reason state models**

Represent:

- off
- starting
- playing
- paused by user
- paused by audio focus
- stopped by user
- stopped by navigation
- stopped by timer
- stopped at book end
- failed

Also define an explicit `ReaderTtsStartRequest` input contract from `ReaderScreen` into the controller. At minimum it should carry:

- `bookId`
- `chapterIndex`
- `charOffset`
- current chapter title or summary text needed for notification fallback
- enough display context to render the first active-state label without deriving it from stale UI state

The same state-model slice must also define the runtime snapshot fields the controller and service share:

- current segment identity
- last confirmed spoken range
- next recoverable character or range
- active pause reason when the session is paused

- [ ] **Step 4: Implement follow suppression timing as a testable pure policy**

Avoid baking the `10` second follow lockout directly into Compose side effects.

- [ ] **Step 5: Re-run the focused model tests**

Run: `./gradlew testDebugUnitTest --tests '*ReaderTtsSegmenterTest' --tests '*ReaderTtsSessionStateTest' --tests '*ReaderTtsFollowSuppressionPolicyTest'`
Expected: PASS

- [ ] **Step 6: Commit the queue and state-model slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSegment.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSegmenter.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsPauseReason.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSessionState.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsPlaybackSnapshot.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsFollowSuppressionPolicy.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsStartRequest.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSegmenterTest.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsSessionStateTest.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsFollowSuppressionPolicyTest.kt
git commit -m "feat: add reader tts segmentation and session models"
```

## Task 3: Add Android TTS Runtime, Foreground Service, And Notification Wiring

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/longerlsx/storyapp/StoryApplication.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/MainActivity.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/app/StoryApp.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/app/StoryAppState.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsEngine.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/AndroidReaderTtsEngine.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsAudioFocusManager.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsNotificationFactory.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsIntentFactory.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsService.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsController.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsControllerTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsAudioFocusManagerTest.kt`

- [ ] **Step 1: Add failing tests for controller, audio-focus, and foreground-service orchestration where practical**

At minimum, add unit coverage for controller state transitions and use instrumentation later for notification behavior.

Run: `./gradlew testDebugUnitTest --tests '*ReaderTtsControllerTest' --tests '*ReaderTtsAudioFocusManagerTest' --tests '*ReaderTtsSessionStateTest'`
Expected: FAIL on missing controller commands, missing app-scope ownership assumptions, missing audio-focus transitions, missing startup-failure handling, or missing service-facing command handling.

- [ ] **Step 2: Declare the Android manifest requirements for TTS playback**

Add:

- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `POST_NOTIFICATIONS`
- `<queries>` for `android.intent.action.TTS_SERVICE`
- a non-exported `ReaderTtsService` with `android:foregroundServiceType="mediaPlayback"`

- [ ] **Step 3: Wrap platform TextToSpeech behind a testable engine interface**

The Android implementation should own:

- engine initialization
- voice enumeration
- speech rate and pitch application
- queueing speech
- receiving `UtteranceProgressListener` callbacks
- signaling when range timing is unavailable so the UI can fall back to segment-level highlight
- fallback to engine default voice when a saved voice disappears

- [ ] **Step 4: Implement the foreground service, notification, and audio-focus manager**

Requirements:

- start from a foreground user action path only
- promote with `ServiceCompat.startForeground(...)`
- expose pause and resume notification actions
- update notification content with current book, chapter, and remaining-time-or-active-state label
- pause on audio-focus loss
- resume on focus gain only if the pause reason was focus loss
- if service startup fails, keep the UI out of fake playing state and surface a reader-local error

- [ ] **Step 5: Add the controller layer as an app-scoped owner that ReaderScreen can observe**

The controller should expose hot state for:

- playback state
- current queued segment identity
- current spoken range
- last confirmed spoken range
- next recoverable character or range
- active pause reason when paused
- remaining timer
- current timer preference including `no timer`
- available voices
- current book id
- current chapter-facing playback summary for notifications
- startup and degraded-mode errors that the reader can render locally

Ownership rule:

- construct the controller once in `StoryApplication`
- never create it inside `remember`, `ReaderScreen`, or another recomposition-sensitive scope
- make the foreground service and UI both talk to that same application-owned instance
- keep the controller runtime snapshot as the single source of truth even when the foreground service is restarted, rebound, or torn down between tests

Concrete wiring path:

- expose the controller from `StoryApplication`
- read it once in `StoryApp`
- pass it explicitly into `ReaderScreen` as a stable dependency
- keep `StoryAppState.openReader(bookId)` as a thin navigation mutator rather than the TTS stop-decision owner
- do cross-book `oldBookId -> newBookId` comparison in `StoryApp` before calling `appState.openReader(bookId)`
- keep `ReaderScreen` as a consumer of controller state and commands only
- make `ReaderScreen` send explicit start/stop/pause/resume requests via `ReaderTtsStartRequest` or equivalent commands instead of leaking raw rendered page state into the service

- [ ] **Step 6: Add notification-permission request plumbing for Android 13+**

The app should request permission before promising drawer controls, but should still handle denial gracefully instead of crashing startup. Add explicit string resources and a reader-local degraded-permission message so the behavior is implementable without guesswork.

- [ ] **Step 7: Add explicit failure-path coverage for startup and timing fallback**

Cover:

- service startup failure leaves playback state non-playing and exposes a local error
- missing `onRangeStart` timing falls back to segment-level highlight state instead of breaking progress UI

- [ ] **Step 8: Run focused JVM tests for the TTS runtime slice**

Run: `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest' --tests '*ReaderTtsSessionStateTest' --tests '*ReaderTtsControllerTest' --tests '*ReaderTtsAudioFocusManagerTest'`
Expected: PASS

- [ ] **Step 9: Commit the runtime and service slice**

Run:
```bash
git add app/src/main/AndroidManifest.xml \
  app/src/main/java/com/longerlsx/storyapp/StoryApplication.kt \
  app/src/main/java/com/longerlsx/storyapp/MainActivity.kt \
  app/src/main/java/com/longerlsx/storyapp/app/StoryApp.kt \
  app/src/main/java/com/longerlsx/storyapp/app/StoryAppState.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsEngine.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/AndroidReaderTtsEngine.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsAudioFocusManager.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsNotificationFactory.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsIntentFactory.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsService.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsController.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsControllerTest.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsAudioFocusManagerTest.kt
git commit -m "feat: add reader tts foreground runtime"
```

## Task 4: Integrate Reader Controls And TTS Settings UI

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeMode.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducer.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheet.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducerTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsControlsTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderUiDeviceHelpers.kt`

- [ ] **Step 1: Add failing reducer and instrumentation coverage for toggle visibility and TTS settings transitions**

Cover:

- `onOpenTtsSettings(current)` toggles between `CHROME_VISIBLE` and `TTS_SETTINGS_EXPANDED`
- explicit close actions from `TTS_SETTINGS_EXPANDED` return to `CHROME_VISIBLE`
- center tap while `TTS_SETTINGS_EXPANDED` is open collapses to `READING_ONLY`
- TTS toggle hidden in immersive mode when playback is off
- TTS toggle visible in chrome-visible mode when playback is off
- TTS toggle visible in immersive mode when playback is on
- TTS toggle does not render inside generic settings, directory, or TTS settings expanded panels
- idle chrome-visible toggle surfaces a lightweight `long-press for settings` discoverability hint
- long-press on the toggle opens the TTS settings panel
- timed playback shows remaining time on the toggle
- untimed playback shows active-state text without a countdown

Run: `./gradlew testDebugUnitTest --tests '*ReaderChromeStateReducerTest'`
Expected: FAIL on missing `TTS_SETTINGS_EXPANDED` transitions or missing reducer behavior.

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsControlsTest`
Expected: FAIL on missing controls and semantics.

- [ ] **Step 2: Add the circular TTS toggle with explicit chrome-state boundaries**

Keep the visibility rules strict so the idle toggle never pollutes immersive reading.

Rules to implement:

- idle toggle renders only in `ReaderChromeMode.CHROME_VISIBLE`
- active stop toggle renders in immersive mode as a separate overlay
- no duplicate toggle inside `SETTINGS_EXPANDED`, `DIRECTORY_OPEN`, or the dedicated TTS settings state
- add a lightweight discoverability affordance on the idle chrome-visible toggle so users can learn that long-press opens settings without turning settings into a separate permanent action

- [ ] **Step 3: Add a dedicated `TTS_SETTINGS_EXPANDED` chrome mode and bind its panel to persisted settings**

Show:

- voice list from current engine
- speech rate control
- pitch control
- timer chips or buttons including `no timer`

Do not mount this panel as a nested section inside `ReaderSettingsSheet`; make it a sibling expanded state in `ReaderChromeMode` and `ReaderChromeStateReducer`.

Exit and transition rules:

- `onOpenTtsSettings(current)` toggles between `CHROME_VISIBLE` and `TTS_SETTINGS_EXPANDED`
- explicit close actions inside the panel return from `TTS_SETTINGS_EXPANDED` to `CHROME_VISIBLE`
- center tap while `TTS_SETTINGS_EXPANDED` is open collapses back to `READING_ONLY`, matching other expanded panels
- opening generic settings or directory while TTS settings is open replaces `TTS_SETTINGS_EXPANDED` instead of stacking panels

- [ ] **Step 4: Apply setting changes without interrupting the active current sentence**

Voice, rate, and pitch changes should take effect from the next short-sentence block; timer changes should take effect immediately. Make the active toggle render remaining time when timed and a stable active-state label when untimed.

- [ ] **Step 5: Re-run the TTS controls instrumentation test**

Run: `./gradlew testDebugUnitTest --tests '*ReaderChromeStateReducerTest'`
Expected: PASS

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsControlsTest`
Expected: PASS

- [ ] **Step 6: Commit the reader control slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeMode.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducer.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheet.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducerTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsControlsTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderUiDeviceHelpers.kt
git commit -m "feat: add reader tts controls"
```

## Task 5: Add Start-Location Mapping, Long-Press Restart, Highlight, And Auto-Follow

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocatorTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsFollowSuppressionPolicyTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderUiDeviceHelpers.kt`

- [ ] **Step 1: Add failing tests for start-location mapping, long-press restart, and follow suppression**

Cover:

- start from current top visible line
- long-press restart absorbs forward over invalid spans
- deterministic chapter and char-offset mapping for both scroll mode and page mode
- manual scroll does not alter the current TTS queue
- manual page turn does not alter the current TTS queue
- first active playback can surface a lightweight one-shot hint that long-pressing body text restarts from that position
- TOC selection and previous/next chapter actions stop the active session instead of pausing it
- after a navigation-triggered stop, the next start request begins from the new current visible top line
- in-reader navigation-triggered stops surface concise feedback that playback ended and a later fresh start will begin from the current visible top line
- follow snaps back after `10` seconds without animation

Run: `./gradlew testDebugUnitTest --tests '*ReaderTextStartLocatorTest' --tests '*ReaderTtsFollowSuppressionPolicyTest'`
Expected: FAIL on missing mapper logic or missing deterministic top-line and forward-absorption rules.

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest`
Expected: FAIL

- [ ] **Step 2: Add an explicit text-hit-testing seam for both reader modes**

Define the concrete seam before implementing mapper math:

- scroll mode: capture long-presses and visible-top sampling from a dedicated reader-text wrapper instead of from the outer tap-only container
- page mode: expose the active laid-out page text plus hit-test callbacks so a press location can map into the current page's `charOffset`
- keep this seam deterministic enough that instrumentation tests can long-press a known body span and assert the resulting restart location
- route TOC selection and previous/next chapter button actions through explicit reader commands that can stop the active TTS session before mutating chapter-selection state
- keep ordinary manual scroll/page-turn gestures on a separate path that never emits stop or pause commands into the controller

- [ ] **Step 3: Implement a unified reader text-location mapper**

It must resolve:

- current visible top-line offset
- long-press text position
- the page containing the currently spoken range

Use the existing pagination and scroll anchor utilities where possible instead of duplicating chapter math. Add explicit UI hooks so both reading modes can report real character offsets into the mapper instead of burying this logic inside raw gesture handlers.

- [ ] **Step 4: Render highlight and auto-follow from the active spoken range**

Primary behavior:

- range highlight when timing callbacks are present

Fallback behavior:

- current-segment highlight when timing is absent

- [ ] **Step 5: Implement manual-follow suppression, snap-back, and chapter-navigation stop boundaries**

Keep:

- `10` second suppression window
- no animation on resume
- behavior active even if the app backgrounds during the suppression interval

Keep navigation semantics distinct from follow suppression:

- TOC selection and previous/next chapter actions stop the active session instead of pausing it
- those navigation-triggered stops do not schedule the `10` second snap-back path
- the next explicit start after such a stop resolves from the newly visible top line rather than from the old session cursor
- show concise reader-local stop feedback for those in-reader navigation stops so users do not confuse them with pause

- [ ] **Step 6: Re-run the focused unit and instrumentation tests**

Run: `./gradlew testDebugUnitTest --tests '*ReaderTextStartLocatorTest' --tests '*ReaderTtsFollowSuppressionPolicyTest' --tests '*ReaderTtsSessionStateTest'`
Expected: PASS

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest`
Expected: PASS

- [ ] **Step 7: Commit the follow and highlight slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocatorTest.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsFollowSuppressionPolicyTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderUiDeviceHelpers.kt
git commit -m "feat: add reader tts follow and highlight"
```

## Task 6: Verify Background Playback, Book Switching, And Reader Regressions

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/app/StoryApp.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/app/StoryAppState.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/StoryApplication.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsBackgroundPlaybackTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsControlsTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsPersistenceTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderContinuousScrollTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderUiDeviceHelpers.kt`

- [ ] **Step 1: Add failing instrumentation coverage for end-to-end playback behavior**

Cover:

- background playback stays active after app backgrounding
- notification pause and resume stay in sync with reader state
- denied `POST_NOTIFICATIONS` is handled without fake-playing UI state
- audio-focus loss pauses and focus gain resumes when appropriate
- opening another book stops the old book session
- TOC selection while TTS is active stops the current session
- previous chapter and next chapter actions while TTS is active stop the current session
- after those stop cases, the next start begins from the newly visible top line
- switching between page and scroll modes does not stop playback
- timer expiry stops playback
- finishing the last readable segment stops playback
- starting a later playback session reuses the last persisted voice, rate, pitch, and timer preset
- `no timer` sessions show active-state text on the toggle and notification instead of countdown

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsBackgroundPlaybackTest`
Expected: FAIL

- [ ] **Step 2: Wire explicit cross-book interruption into an app-level reader-opening wrapper**

Cross-book opening is the app-level stop boundary. Implement that handoff in `StoryApp` as a wrapper around `appState.openReader(bookId)` so both bookshelf selection and import completion flow through the same comparison logic before navigation state mutates. Keep TOC and previous/next chapter stop behavior in the reader-layer task above rather than duplicating it here.

Rule:

- compare `oldBookId` and `newBookId` before stopping playback
- only stop when both ids are non-null and different
- same-book re-entry, restoration, or reader re-open should not interrupt the active session

- [ ] **Step 3: Add deterministic test teardown for the app-scoped TTS runtime**

Extend the test harness so every instrumentation test can:

- stop any running `ReaderTtsService`
- reset the app-scoped controller singleton to an idle state
- clear the controller runtime snapshot, error channel, and active pause reason
- abandon any held audio focus and unregister focus listeners
- dismiss or replace any lingering foreground notification before the next scenario starts
- clear persisted TTS settings only when the test explicitly requires a clean first-run scenario

Document the reset seam explicitly:

- add a test-only reset/close hook on the app-scoped TTS owner in `StoryApplication`
- name that hook explicitly, for example `StoryApplication.resetReaderTtsForTest()`, so every TTS-related instrumentation test shares one reset contract
- expose one shared instrumentation helper, for example `ReaderUiDeviceHelpers.resetReaderTtsEnvironment()`, that calls the hook and performs service/audio-focus/notification cleanup in a fixed order
- make the instrumentation helper call that hook before each TTS-related scenario
- make the same helper run from both `@Before` and `@After` in TTS-related instrumentation suites instead of relying on per-test ad-hoc cleanup
- make the helper await service-idle completion rather than assuming `stopService(...)` synchronously clears the runtime

Put this reset path in `ReaderUiDeviceHelpers.kt` or an equivalent shared helper before depending on background-playback assertions.

- [ ] **Step 4: Run the TTS-specific instrumentation subset**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsControlsTest,com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest,com.longerlsx.storyapp.feature.reader.ReaderTtsBackgroundPlaybackTest`
Expected: PASS

- [ ] **Step 5: Run reader regression unit tests**

Run: `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest' --tests '*ReaderTtsSegmenterTest' --tests '*ReaderTtsSessionStateTest' --tests '*ReaderTtsAudioFocusManagerTest' --tests '*ReaderTextStartLocatorTest' --tests '*ReaderTtsFollowSuppressionPolicyTest' --tests '*ReaderChromeStateReducerTest' --tests '*ReaderPageLinePaginatorTest' --tests '*ReaderPageAnchorMapperTest' --tests '*ReaderScrollFeedAnchorMapperTest'`
Expected: PASS

- [ ] **Step 6: Run key reader instrumentation regressions**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReaderContinuousScrollTest,com.longerlsx.storyapp.feature.reader.ReaderSettingsPersistenceTest,com.longerlsx.storyapp.feature.reader.ReaderTtsControlsTest,com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest,com.longerlsx.storyapp.feature.reader.ReaderTtsBackgroundPlaybackTest`
Expected: PASS

- [ ] **Step 7: Install the latest debug build**

Run: `./gradlew installDebug`
Expected: `Installed on 1 device.`

- [ ] **Step 8: Commit the regression and background-playback slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/app/StoryApp.kt \
  app/src/main/java/com/longerlsx/storyapp/app/StoryAppState.kt \
  app/src/main/java/com/longerlsx/storyapp/StoryApplication.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsBackgroundPlaybackTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsControlsTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsPersistenceTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderContinuousScrollTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderUiDeviceHelpers.kt
git commit -m "feat: finish reader background tts"
```
