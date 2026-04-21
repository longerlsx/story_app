# Reader Interaction Refresh Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework the reader chrome into the agreed immersive two-layer interaction model, with direct day/night toggle, in-place settings expansion, and separate remembered day/night themes.

**Architecture:** Keep `ReaderScreen` as the orchestration point, but split state changes into a few focused helpers so the screen does not become a pure pile of booleans. Upgrade `ReaderSettings` and its store first, then re-layer the scroll reader UI around a top bar, chapter-navigation row, primary bottom bar, and expandable settings content that replaces the chapter row in place.

**Tech Stack:** Kotlin, Jetpack Compose Material3/Foundation, local properties-file persistence, JUnit, Android instrumented tests

---

## File Map

- Modify: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderAppearanceMode.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStore.kt`
- Modify: `app/src/test/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStoreTest.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderThemePreset.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderThemePalette.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderThemeResolver.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderThemeResolverTest.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeMode.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducer.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducerTest.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTocSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsPersistenceTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReadingPositionRestoreTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderAppearanceToggleTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsExpansionTest.kt`

## Task 1: Upgrade Reader Settings Model

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderAppearanceMode.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStore.kt`
- Modify: `app/src/test/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStoreTest.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderThemeResolverTest.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderThemeResolver.kt`

- [ ] **Step 1: Extend `ReaderSettingsStoreTest` with a failing case for `appearanceMode`, `dayThemePreset`, `nightThemePreset`, `paragraphSpacingEm`, and `brightness` persistence**
- [ ] **Step 2: Add a failing unit test for resolving the active theme from appearance mode plus day/night remembered presets**
- [ ] **Step 3: Run `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest' --tests '*ReaderThemeResolverTest'` and verify it fails**
- [ ] **Step 4: Replace the old single-theme / edge-padding-oriented settings model with the new fields and persist them in `ReaderSettingsStore`**
- [ ] **Step 5: Implement the minimal theme resolver helper**
- [ ] **Step 6: Re-run the same unit tests and verify they pass**

## Task 2: Lock Chrome-State Rules with Pure Tests

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeMode.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducer.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderChromeStateReducerTest.kt`

- [ ] **Step 1: Write failing reducer tests for the agreed chrome rules**
- [ ] **Step 2: Cover at least these transitions:**
- [ ] `reading -> chromeVisible` on center tap
- [ ] `chromeVisible -> reading` on center tap
- [ ] `chromeVisible -> settingsExpanded` on settings tap
- [ ] `settingsExpanded -> reading` on center tap
- [ ] `settingsExpanded -> directoryOpen` closes settings first
- [ ] `directoryOpen -> reading` after chapter selection
- [ ] `previous/next chapter` keeps chrome visible
- [ ] **Step 3: Run `./gradlew testDebugUnitTest --tests '*ReaderChromeStateReducerTest'` and verify it fails**
- [ ] **Step 4: Implement the minimal reducer and value object(s)**
- [ ] **Step 5: Re-run the reducer test and verify it passes**

## Task 3: Rebuild the Reader Chrome Layout

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTocSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsExpansionTest.kt`

- [ ] **Step 1: Write failing instrumentation coverage for the two-layer bottom area and in-place settings expansion**
- [ ] **Step 2: Verify these behaviors in tests:**
- [ ] top and bottom chrome reveal together on center tap
- [ ] chapter row shows `上一章 / 进度 / 下一章`
- [ ] primary bar shows `目录 / 日夜切换 / 设置`
- [ ] tapping `设置` replaces the chapter row with settings content
- [ ] tapping `目录` closes settings if needed and opens the directory layer
- [ ] directory selection returns to reading-only state
- [ ] **Step 3: Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderChromeTest,com.longerlsx.storyapp.feature.reader.ReaderSettingsExpansionTest` and verify it fails**
- [ ] **Step 4: Refactor `ReaderScreen` to use the chrome-state reducer and render the new layered structure**
- [ ] **Step 5: Replace the current bottom-sheet settings with in-place expanded settings content**
- [ ] **Step 6: Adjust the directory surface so it behaves like the agreed drawer/expanded layer**
- [ ] **Step 7: Re-run the same instrumentation tests and verify they pass**

## Task 4: Add Direct Day/Night Toggle and Theme Memory

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsPersistenceTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderAppearanceToggleTest.kt`

- [ ] **Step 1: Write failing instrumentation tests for direct day/night toggle and remembered day/night themes**
- [ ] **Step 2: Cover at least these behaviors:**
- [ ] the middle primary action toggles day/night immediately
- [ ] toggling day/night does not reset reading mode or typography
- [ ] selecting a day theme only affects future day-mode state
- [ ] selecting a night theme only affects future night-mode state
- [ ] relaunch restores appearance mode and remembered theme for that mode
- [ ] **Step 3: Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderAppearanceToggleTest,com.longerlsx.storyapp.feature.reader.ReaderSettingsPersistenceTest` and verify it fails**
- [ ] **Step 4: Implement the day/night toggle and active-mode-scoped theme picker in the expanded settings UI**
- [ ] **Step 5: Re-run the same instrumentation tests and verify they pass**

## Task 5: Replace Edge Padding with Paragraph Spacing

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStore.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Extend: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsExpansionTest.kt`

- [ ] **Step 1: Extend the failing settings-expansion test to expect paragraph spacing instead of edge padding**
- [ ] **Step 2: Run the focused test and verify it fails**
- [ ] **Step 3: Replace the old padding control with paragraph spacing state and apply it to scroll-reader paragraph rendering**
- [ ] **Step 4: Re-run the focused test and verify it passes**

## Task 6: Regression and Install

**Files:**
- Verify the reader/import suite remains green after the interaction refresh

- [ ] **Step 1: Run `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest' --tests '*ReaderThemeResolverTest' --tests '*ReaderChromeStateReducerTest' --tests '*ReaderTapZoneTest' --tests '*ReaderPageAnchorMapperTest' --tests '*OpeningChapterSelectorTest' --tests '*ReaderRestorePolicyTest' --tests '*ScrollAnchorMapperTest'`**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.importer.ExternalImportFlowTest,com.longerlsx.storyapp.feature.reader.ReaderChromeTest,com.longerlsx.storyapp.feature.reader.ReaderSettingsExpansionTest,com.longerlsx.storyapp.feature.reader.ReaderAppearanceToggleTest,com.longerlsx.storyapp.feature.reader.ReaderSettingsPersistenceTest,com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReadingPositionRestoreTest`**
- [ ] **Step 3: Run `./gradlew installDebug` and verify the newest build is on the emulator**
