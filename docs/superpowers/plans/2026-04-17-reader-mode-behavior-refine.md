# Reader Mode Behavior Refine Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make page mode honor previous-chapter last-page behavior, convert scroll mode into a true continuous chapter stream, and keep a persistent top-left back/chapter header.

**Architecture:** Keep `ReaderScreen` as the orchestration point, but split mode-specific logic into focused helpers: one for page-boundary behavior, one for continuous-scroll active chapter resolution and anchor mapping, and one for the persistent compact header. Scroll mode should move from a single `ScrollState` chapter surface to a `LazyListState` multi-chapter stream while preserving the shared persisted anchor model.

**Tech Stack:** Kotlin, Jetpack Compose Foundation/Material3, properties-file persistence, JUnit, Android instrumented tests

---

## File Map

- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageBoundaryResolver.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderPageBoundaryResolverTest.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScrollChapterResolver.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderScrollChapterResolverTest.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScrollFeedAnchorMapper.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderScrollFeedAnchorMapperTest.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderContinuousScrollTest.kt`

## Task 1: Add Pure Helpers for New Mode Semantics

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageBoundaryResolver.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderPageBoundaryResolverTest.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScrollChapterResolver.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderScrollChapterResolverTest.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScrollFeedAnchorMapper.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderScrollFeedAnchorMapperTest.kt`

- [ ] **Step 1: Write failing unit tests for previous-chapter-last-page resolution, active visible chapter selection, and continuous-scroll anchor mapping**
- [ ] **Step 2: Run `./gradlew testDebugUnitTest --tests '*ReaderPageBoundaryResolverTest' --tests '*ReaderScrollChapterResolverTest' --tests '*ReaderScrollFeedAnchorMapperTest'` and verify they fail**
- [ ] **Step 3: Implement the minimal helpers that satisfy those tests**
- [ ] **Step 4: Re-run the same unit tests and verify they pass**

## Task 2: Update Instrumentation Coverage Before UI Changes

**Files:**
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderContinuousScrollTest.kt`

- [ ] **Step 1: Extend page-mode instrumentation to assert paging backward across a chapter boundary lands near the previous chapter end**
- [ ] **Step 2: Extend chrome instrumentation to assert the compact top-left back/chapter header remains visible**
- [ ] **Step 3: Add a failing continuous-scroll instrumentation test covering cross-chapter scrolling and the absence of previous/next chapter buttons in scroll mode**
- [ ] **Step 4: Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReaderChromeTest,com.longerlsx.storyapp.feature.reader.ReaderContinuousScrollTest` and verify it fails**

## Task 3: Rework ReaderScreen for Continuous Scroll and Persistent Header

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`

- [ ] **Step 1: Replace the transient top bar with a persistent compact top-left header that always shows back + current chapter context**
- [ ] **Step 2: Change scroll mode from single-chapter `ScrollState` rendering to a continuous multi-chapter `LazyColumn` feed**
- [ ] **Step 3: Drive current chapter display and progress saving from the visible chapter item nearest the top of the feed**
- [ ] **Step 4: Remove the chapter-navigation row in scroll mode while keeping it in page mode**
- [ ] **Step 5: Apply previous-boundary page behavior so paging backward from a chapter start opens the previous chapter at its last page**
- [ ] **Step 6: Re-run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReaderChromeTest,com.longerlsx.storyapp.feature.reader.ReaderContinuousScrollTest` and verify it passes**

## Task 4: Regression and Install

**Files:**
- Verify existing reader and import behavior stays green after the mode split

- [ ] **Step 1: Run `./gradlew testDebugUnitTest --tests '*ReaderPageBoundaryResolverTest' --tests '*ReaderScrollChapterResolverTest' --tests '*ReaderScrollFeedAnchorMapperTest' --tests '*ReaderPageLinePaginatorTest' --tests '*ReaderSettingsStoreTest' --tests '*ReaderBrightnessResolverTest'`**
- [ ] **Step 2: Run `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReaderChromeTest,com.longerlsx.storyapp.feature.reader.ReaderContinuousScrollTest,com.longerlsx.storyapp.feature.reader.ReaderSettingsPersistenceTest,com.longerlsx.storyapp.feature.importer.ExternalImportFlowTest`**
- [ ] **Step 3: Run `./gradlew installDebug` and verify the newest build is on the emulator**
