# Reader Immersive Chrome and Page Mode Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add immersive reader controls and a first usable horizontal page-turn mode that shares the same persisted anchor as scroll mode.

**Architecture:** Keep `ReaderScreen` as the orchestration point, but split mode-specific behavior into focused helpers: one for tap-zone actions and one for page anchor mapping. Persist the chosen reading mode inside the existing reader settings store so restarts and mode switches are stable without introducing a ViewModel refactor yet.

**Tech Stack:** Kotlin, Jetpack Compose Material3/Foundation pager, local properties-file persistence, JUnit, Android instrumented tests

---

## File Map

- Modify: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStore.kt`
- Modify: `app/src/test/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStoreTest.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTapZone.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderTapZoneTest.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageAnchorMapper.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderPageAnchorMapperTest.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsPersistenceTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`

## Task 1: Persist Reading Mode in Settings

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStore.kt`
- Modify: `app/src/test/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStoreTest.kt`

- [ ] **Step 1: Extend the failing settings store test to assert `readingMode` survives save/load**
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest'` and verify it fails**
- [ ] **Step 3: Save and restore `readingMode` in `ReaderSettingsStore`**
- [ ] **Step 4: Re-run the same unit test and verify it passes**

## Task 2: Add Pure Helpers for Page-Mode Behavior

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTapZone.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderTapZoneTest.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageAnchorMapper.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderPageAnchorMapperTest.kt`

- [ ] **Step 1: Write failing tests for center-tap toggle zones and page lookup from a saved char offset**
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests '*ReaderTapZoneTest' --tests '*ReaderPageAnchorMapperTest'` and verify they fail**
- [ ] **Step 3: Implement the minimal helpers for tap-zone resolution and page-anchor mapping**
- [ ] **Step 4: Re-run the same unit tests and verify they pass**

## Task 3: Add Immersive Chrome and Horizontal Page-Turn

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsPersistenceTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`

- [ ] **Step 1: Extend instrumentation coverage to assert reading mode persists and page mode can reopen at the same chapter**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderSettingsPersistenceTest,com.longerlsx.storyapp.feature.reader.ReaderPageModeTest` and verify it fails**
- [ ] **Step 3: Refactor `ReaderScreen` to hide chrome by default, reveal it on center tap, and auto-hide it after idle**
- [ ] **Step 4: Add a reading-mode row to the settings sheet and wire it to the persisted settings store**
- [ ] **Step 5: Implement page-mode rendering with horizontal pager, left/right tap zones, and shared anchor persistence**
- [ ] **Step 6: Re-run the instrumentation tests and verify they pass**

## Task 4: Regression and Install

**Files:**
- Verify existing reader/import tests remain green after the mode split

- [ ] **Step 1: Run `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest' --tests '*ReaderTapZoneTest' --tests '*ReaderPageAnchorMapperTest' --tests '*ReaderRestorePolicyTest' --tests '*OpeningChapterSelectorTest' --tests '*ScrollAnchorMapperTest'`**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderChromeTest,com.longerlsx.storyapp.feature.reader.ReaderSettingsPersistenceTest,com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReadingPositionRestoreTest,com.longerlsx.storyapp.feature.importer.ExternalImportFlowTest`**
- [ ] **Step 3: Run `./gradlew installDebug` and verify the newest build is on the emulator**
