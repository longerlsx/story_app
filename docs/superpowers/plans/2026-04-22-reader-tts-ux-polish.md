# Reader TTS UX Polish Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish the reader TTS experience so voice choices are constrained to mainland Chinese, long-press restart visibly jumps immediately, the reader keeps one stable bottom action bar with a unified settings panel, and chrome auto-hide respects recent user interaction.

**Architecture:** Keep the playback/service stack intact and treat this work as a UI/interaction refinement layer on top of the existing TTS runtime. Move TTS configuration into the existing reader settings surface, collapse the old dedicated `TTS_SETTINGS_EXPANDED` chrome path, and use one shared toggle-label model so bottom-bar, immersive, and notification-facing labels stay aligned. Notification controls should expose explicit `暂停/继续` and `停止` actions so the reader can stay “start/stop-first” without hiding pause semantics from the system surface. For long-press restart, resolve the new start location before dispatch and hold a short-lived local visual anchor until live playback timing catches up. Rework chrome auto-hide around a “last meaningful chrome interaction” timestamp so visible controls stay up while the user is actively tapping them.

**Tech Stack:** Kotlin, Jetpack Compose, Android `TextToSpeech`, Coroutines/Flow, JUnit, Android instrumented tests

---

## File Map

- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeMode.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducer.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/AndroidReaderTtsEngine.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsVoiceOption.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsTab.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsVoiceCatalog.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducerTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocatorTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsVoiceCatalogTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsControlsTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsExpansionTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheetTest.kt`

## Task 1: Constrain And Deduplicate Voice Choices

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsVoiceCatalog.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/AndroidReaderTtsEngine.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsVoiceOption.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsVoiceCatalogTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsExpansionTest.kt`

- [ ] **Step 1: Write failing tests for mainland-only filtering and local-first dedupe**

Cover:

- keep only `zh-CN` / mainland Mandarin voices
- exclude `Chinese (Taiwan)` and non-Chinese voices
- when both `local` and `network` variants exist for the same mainland voice family, keep `local`
- if no mainland voice exists, return an empty selectable list instead of falling back to Taiwan or English
- if a persisted `voiceName` is no longer present or gets filtered out, playback falls back to the engine default and the settings UI stops presenting the stale selection as still valid

Run: `./gradlew testDebugUnitTest --tests '*ReaderTtsVoiceCatalogTest'`
Expected: FAIL because voice filtering is currently embedded in `AndroidReaderTtsEngine` and still exposes Taiwan/network-heavy lists.

- [ ] **Step 2: Extract a pure voice-catalog helper**

Introduce a small mapper that accepts platform voice descriptors and returns app-facing options. Keep the persisted selection key as the real engine `voice.name`; only the visible list should be filtered and deduplicated.

Define a canonical family key for dedupe so the same mainland voice exposed as `local` and `network` collapses deterministically. Also define the stale-selection fallback: when the saved voice is no longer selectable, the runtime uses the engine default voice and the UI clears the visible selected state for that session instead of pretending the old choice is still active.

- [ ] **Step 3: Wire Android TTS initialization through the new catalog**

`AndroidReaderTtsEngine` should stop hand-rolling the sorted list and instead delegate to the catalog helper so the same rules apply on first-load, preload, and later refreshes.

- [ ] **Step 4: Add the empty-mainland fallback behavior in the settings UI**

When the filtered list is empty:

- show a message such as `已筛除非大陆中文音色，当前将使用系统默认音色`
- keep playback available through the engine default voice
- do not re-introduce Taiwan or other-language choices just to avoid an empty list
- if there is a previously saved `voiceName` that is now invalid, surface a small “当前使用系统默认音色” status in the `朗读` tab

- [ ] **Step 5: Re-run the focused voice tests**

Run:

- `./gradlew testDebugUnitTest --tests '*ReaderTtsVoiceCatalogTest'`
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsSettingsExpansionTest`

Expected: PASS, with the settings panel showing only mainland-Chinese voice options in the fake-engine test path.

- [ ] **Step 6: Commit the voice-catalog slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsVoiceCatalog.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/AndroidReaderTtsEngine.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsVoiceOption.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsVoiceCatalogTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsExpansionTest.kt
git commit -m "feat: narrow reader tts voice choices"
```

## Task 2: Collapse Reader And TTS Settings Into One Tabbed Sheet

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsTab.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeMode.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducer.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducerTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheetTest.kt`

- [ ] **Step 1: Write failing tests for the new chrome model**

Cover:

- `设置` always opens one unified sheet
- the sheet has `阅读` and `朗读` tabs
- the last selected tab is restored when reopening the sheet
- the very first open defaults to `朗读` when the current book is actively朗读 or paused, otherwise defaults to `阅读`
- the old dedicated `TTS_SETTINGS_EXPANDED` flow no longer exists

Run:

- `./gradlew testDebugUnitTest --tests '*ReaderChromeStateReducerTest'`
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsSettingsSheetTest`

Expected: FAIL because the current chrome state still forks `SETTINGS_EXPANDED` and `TTS_SETTINGS_EXPANDED`, and the TTS sheet still renders its own standalone title/actions.

- [ ] **Step 2: Simplify chrome state to one settings surface**

Remove the dedicated TTS-expanded chrome path. Keep `ReaderChromeMode` responsible only for:

- reading only
- chrome visible
- settings expanded
- directory open

Track the active settings tab separately in `ReaderScreen` with `rememberSaveable`, because it is session UI state rather than persisted reader preference.

The default resolution rule must be explicit:

- if the current book is `PLAYING`, `STARTING`, `PAUSED_BY_USER`, or `PAUSED_BY_AUDIO_FOCUS`, the first-ever open lands on `朗读`
- otherwise the first-ever open lands on `阅读`
- after the first explicit tab switch, reopening the sheet restores the last tab used in the current reader session

- [ ] **Step 3: Turn the current settings sheet into a tab container**

Refactor `ReaderSettingsSheet` so it owns:

- top tab row: `阅读` / `朗读`
- a shared scroll container
- a slot or internal branch for the reading-settings body
- a slot or internal branch for the TTS-settings body

Strip standalone title/footer actions out of `ReaderTtsSettingsSheet`; it should become a section-level body used inside the unified sheet, not a second competing sheet.

- [ ] **Step 4: Keep tab memory local and predictable**

Implement:

- the first-ever open follows the explicit default rule above
- after the first explicit tab switch, later opens restore the last tab used in the current reader session
- switching tabs does not close the sheet
- center tap or explicit close exits the unified sheet back to `CHROME_VISIBLE`

- [ ] **Step 5: Re-run the chrome and settings-sheet tests**

Run:

- `./gradlew testDebugUnitTest --tests '*ReaderChromeStateReducerTest'`
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsSettingsSheetTest`

Expected: PASS

- [ ] **Step 6: Commit the unified-settings slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsTab.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeMode.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducer.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheet.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducerTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheetTest.kt
git commit -m "feat: unify reader and tts settings"
```

## Task 3: Keep One Stable Bottom Bar And Restore Timer Labels

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsNotificationFactory.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsBackgroundPlaybackTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsControlsTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheetTest.kt`

- [ ] **Step 1: Write failing UI tests for persistent bottom actions and timer text**

Cover:

- bottom bar always stays `[目录][夜间/日间][设置][朗读/继续朗读/停止朗读]`
- opening settings or directory does not remove the TTS control
- while `CHROME_VISIBLE` is open, tapping `日间/夜间`, `朗读`, or `上一章/下一章` resets the `3` second auto-hide countdown
- opening `设置` or `目录` does not need to reset the countdown because the UI leaves the plain chrome state instead
- timed playback shows the remaining label on the bottom-bar TTS action
- immersive playback shows `停止朗读 · 28m` style copy when a timer exists
- `不定时` shows stable state text instead of a missing label
- notification actions expose `暂停/继续` and `停止`, with icons and labels that match those semantics

Run:

- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderChromeTest,com.longerlsx.storyapp.feature.reader.ReaderTtsControlsTest,com.longerlsx.storyapp.feature.reader.ReaderTtsSettingsSheetTest,com.longerlsx.storyapp.feature.reader.ReaderTtsBackgroundPlaybackTest`

Expected: FAIL because the current auto-hide timer is keyed only off `chromeMode`, the expanded chrome paths null out `ttsToggleState`, and the immersive stop row cannot append the remaining-time label.

- [ ] **Step 2: Give the TTS toggle one shared label model**

Refactor `ReaderTtsToggleUiState` so it can express:

- inactive: `朗读`
- active with no timer: `停止朗读`
- active with timer: `停止朗读 · 28m`
- paused with no timer: `继续朗读`
- paused with timer: `继续朗读 · 28m`

Drive both the bottom-bar action and the immersive row from the same label source. Keep notification copy aligned to the same “remaining time if timed, stable state text if untimed” rule. In notification space, add a second explicit `停止` action so pause and stop are both user-visible. Remove the old `长按设置` hint entirely.

- [ ] **Step 3: Rework `CHROME_VISIBLE` auto-hide around the last interaction timestamp**

Implement the `3` second closeout as:

- `CHROME_VISIBLE` starts a countdown
- any explicit button tap inside the plain bottom chrome resets the countdown
- `上一章/下一章` row taps reset the countdown
- opening `设置` or `目录` ends the plain-chrome countdown by switching to expanded mode rather than “resetting” it

Do not let appearance-mode toggles or play/pause taps silently expire the existing timer anymore.

- [ ] **Step 4: Keep the bottom bar mounted in every non-reading-only chrome mode**

Do not special-case settings or directory views by dropping the TTS control. The only thing that should change is the content above the bar.

- [ ] **Step 5: Reconcile the unified settings sheet with live timer/status display**

Inside the `朗读` tab:

- keep showing `剩余时间：...` when a timer is active
- show current status text when active but untimed
- keep stop/play controls out of the sheet footer; they now belong to the stable bottom bar

- [ ] **Step 6: Re-run the focused controls tests**

Run:

- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderChromeTest,com.longerlsx.storyapp.feature.reader.ReaderTtsControlsTest,com.longerlsx.storyapp.feature.reader.ReaderTtsSettingsSheetTest,com.longerlsx.storyapp.feature.reader.ReaderTtsBackgroundPlaybackTest`

Expected: PASS

- [ ] **Step 7: Commit the bottom-bar and label slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheet.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTtsNotificationFactory.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsBackgroundPlaybackTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsControlsTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheetTest.kt
git commit -m "feat: stabilize reader tts controls"
```

## Task 4: Make Long-Press Restart Visibly Jump Immediately

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocatorTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt`

- [ ] **Step 1: Write failing tests for long-press restart feedback**

Cover:

- long-press still uses forward absorption to skip blank/punctuation-only starts
- after long-press restart, the visible page/scroll position jumps immediately to the new start
- highlight updates immediately instead of waiting for the next TTS timing callback
- the UI shows a short confirmation cue such as `从这里重新朗读`
- once live playback timing catches up, the temporary override clears and normal follow resumes

Run:

- `./gradlew testDebugUnitTest --tests '*ReaderTextStartLocatorTest'`
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest`

Expected: FAIL because restart currently clears the playback snapshot first, which creates a visible gap before highlight/follow catches up.

- [ ] **Step 2: Resolve restart location before dispatch**

Move the “pressed offset -> absorbed readable body offset” decision into the UI command path so the same normalized location can be used for:

- the outgoing restart request
- immediate local follow/highlight state

Do not leave normalization solely inside the service for this path, or the UI will keep guessing after dispatch.

- [ ] **Step 3: Add a short-lived local restart anchor in `ReaderScreen`**

Maintain a temporary override containing:

- chapter index
- resolved char offset
- provisional highlight range
- creation timestamp
- a short confirmation-message token used to render `从这里重新朗读`

Use it to drive immediate scroll/page follow and coarse highlight until the runtime snapshot reports a segment at or beyond that location. Clear it when:

- playback snapshot catches up
- playback stops
- the current book changes
- the user manually stops or opens another book

Define precedence explicitly:

- while the local restart anchor exists, follow/highlight use it first
- once the runtime snapshot reaches the same chapter and an equal-or-later character offset, the local anchor is discarded and the live snapshot becomes the single source of truth again
- if playback restart fails, clear the local anchor immediately so stale highlight does not linger

- [ ] **Step 4: Re-run the restart/follow tests**

Run:

- `./gradlew testDebugUnitTest --tests '*ReaderTextStartLocatorTest'`
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest`

Expected: PASS

- [ ] **Step 5: Commit the restart-feedback slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocatorTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt
git commit -m "feat: tighten reader tts restart feedback"
```

## Task 5: Run Focused Regression And Manual Acceptance

**Files:**
- Modify: `docs/superpowers/plans/2026-04-22-reader-tts-ux-polish.md`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsControlsTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsBackgroundPlaybackTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsExpansionTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsSheetTest.kt`

- [ ] **Step 1: Run the full focused unit suite**

Run:

- `./gradlew testDebugUnitTest --tests '*ReaderChromeStateReducerTest' --tests '*ReaderTextStartLocatorTest' --tests '*ReaderTtsVoiceCatalogTest'`

Expected: PASS

- [ ] **Step 2: Run the full focused instrumented suite**

Run:

- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderChromeTest,com.longerlsx.storyapp.feature.reader.ReaderTtsControlsTest,com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest,com.longerlsx.storyapp.feature.reader.ReaderTtsBackgroundPlaybackTest,com.longerlsx.storyapp.feature.reader.ReaderTtsSettingsExpansionTest,com.longerlsx.storyapp.feature.reader.ReaderTtsSettingsSheetTest`

Expected: PASS

- [ ] **Step 3: Perform manual acceptance on the connected Android client**

Verify:

- `设置` opens one tabbed sheet
- switching `阅读` / `朗读` tabs keeps the bottom bar visible
- `朗读` no longer depends on long-press to reach settings
- voice list only shows mainland Chinese options
- a timed session shows `停止朗读 · <time>` in reader chrome and immersive mode
- a paused timed session shows `继续朗读 · <time>`
- tapping `日间/夜间`, `朗读`, and chapter-step controls while chrome is visible keeps the `3` second auto-hide window alive
- notification copy still shows the same remaining-time-or-state rule
- notification actions expose both `暂停/继续` and `停止`
- long-press正文 restart immediately moves the reading surface and highlight

- [ ] **Step 4: Update the plan checkboxes and note any accepted regressions**

If any planned step is intentionally deferred, write the reason directly into this plan before handoff.

- [ ] **Step 5: Commit the polish and updated plan**

Run:
```bash
git add docs/superpowers/plans/2026-04-22-reader-tts-ux-polish.md
git commit -m "docs: update reader tts ux polish plan status"
```
