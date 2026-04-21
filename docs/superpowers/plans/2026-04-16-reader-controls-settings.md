# Reader Controls and Settings Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add chapter navigation controls and persistent reader settings so the vertical reader feels usable for daily reading.

**Architecture:** Keep the current single-screen reader structure, but extract two focused units: chapter navigation controls and a lightweight persisted reader settings store. Avoid a large ViewModel refactor in this slice; keep state local to `ReaderScreen` and persist only the settings and reading anchor required for the UX.

**Tech Stack:** Kotlin, Jetpack Compose Material3, local properties-file persistence, JUnit, Android instrumented tests

---

## File Map

- Modify: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderThemePreset.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStore.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/StoryApplication.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/app/StoryApp.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStoreTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsPersistenceTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`

## Task 1: Persist Reader Settings

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderThemePreset.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStore.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/reader/ReaderSettingsStoreTest.kt`

- [ ] **Step 1: Write the failing settings store test**
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest'` and verify it fails**
- [ ] **Step 3: Add theme preset support to `ReaderSettings` and implement the minimal properties-backed store**
- [ ] **Step 4: Re-run `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest'` and verify it passes**

## Task 2: Add Chapter Navigation Controls

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`

- [ ] **Step 1: Extend the failing reader chrome test to cover previous/next chapter buttons**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderChromeTest` and verify it fails**
- [ ] **Step 3: Implement previous/next chapter controls with disabled states at boundaries**
- [ ] **Step 4: Re-run the same instrumentation test and verify it passes**

## Task 3: Add Reader Settings UI and Persistence

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/StoryApplication.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/app/StoryApp.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsSheet.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderSettingsPersistenceTest.kt`

- [ ] **Step 1: Write the failing instrumentation test for changing settings and reopening the reader**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderSettingsPersistenceTest` and verify it fails**
- [ ] **Step 3: Implement a settings sheet with font size, line spacing, horizontal padding, and theme preset controls**
- [ ] **Step 4: Persist changes through the settings store and apply them to reader content rendering**
- [ ] **Step 5: Re-run the settings instrumentation test and verify it passes**

## Task 4: Regression and Install

**Files:**
- Verify existing reader and import tests remain green

- [ ] **Step 1: Run `./gradlew testDebugUnitTest --tests '*ReaderSettingsStoreTest' --tests '*OpeningChapterSelectorTest' --tests '*ScrollAnchorMapperTest' --tests '*ReaderRestorePolicyTest'`**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderChromeTest,com.longerlsx.storyapp.feature.reader.ReaderSettingsPersistenceTest,com.longerlsx.storyapp.feature.reader.ReadingPositionRestoreTest,com.longerlsx.storyapp.feature.importer.ExternalImportFlowTest`**
- [ ] **Step 3: Run `./gradlew installDebug` and verify the newest build is on the emulator**
