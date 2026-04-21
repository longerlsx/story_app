# Reader Topbar And Page Transition Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the reader top area match the intended two-state chrome design and make page-mode cross-chapter transitions feel like the same horizontal page-turn flow as in-chapter paging.

**Architecture:** Keep `ReaderScreen` as the orchestration point, but separate the immersive top header from the chrome-visible top bar and tighten page-mode content loading so cross-chapter paging always restores against the correct chapter content. Reuse the existing anchor model and page pagination helpers, while adjusting instrumentation fixtures so the tests verify the intended interaction states instead of brittle incidental layout details.

**Tech Stack:** Kotlin, Jetpack Compose Foundation/Material3, Coroutines/Flow, JUnit, Android instrumented tests

---

## File Map

- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderUiDeviceHelpers.kt`
- Verify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderActiveChapterResolver.kt`
- Verify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageBoundaryResolver.kt`

## Task 1: Lock In Failing Interaction Tests

**Files:**
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderUiDeviceHelpers.kt`

- [ ] **Step 1: Update the page-boundary test to express the desired user path clearly**
  The test should prove that from a later chapter, paging backward lands near the previous chapter end rather than its beginning.

- [ ] **Step 2: Update chrome tests to cover the two top states**
  Add or adjust assertions so immersive mode expects only `back arrow + chapter` and chrome-visible mode expects the full top bar with `返回 / 书名+章节 / ...`.

- [ ] **Step 3: Make helper clicks resilient without relying on stale UI objects**
  Keep the helpers focused on user-visible labels first, with stable fallbacks only when needed.

- [ ] **Step 4: Run the focused instrumentation subset and verify it fails for the new expectations**

Run:
```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,\
com.longerlsx.storyapp.feature.reader.ReaderChromeTest
```

Expected: FAIL on the new top-bar and page-transition expectations before implementation.

## Task 2: Rebuild The Reader Top Chrome Hierarchy

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`

- [ ] **Step 1: Replace the current floating immersive header with a light inline reading header**
  It should render only a back arrow plus the current chapter title, without a capsule background or strong elevation.

- [ ] **Step 2: Add a dedicated chrome-visible top bar**
  The top bar should appear only when the reader chrome is visible and should render `返回 / 书名+章节 / ...`.

- [ ] **Step 3: Ensure the immersive header and full top bar are mutually exclusive**
  When chrome appears, the small reading header must disappear completely. When chrome hides, the small reading header must come back.

- [ ] **Step 4: Keep scroll mode and page mode on the same top-state model**
  Do not fork the top chrome separately by mode; only the bottom controls should stay mode-aware.

- [ ] **Step 5: Run the chrome-focused instrumentation tests and verify they pass**

Run:
```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderChromeTest#centerTapRevealsTwoLayerReaderChrome,\
com.longerlsx.storyapp.feature.reader.ReaderChromeTest#directorySelectionReturnsToReadingOnly
```

Expected: PASS with the new top chrome behavior.

## Task 3: Unify Cross-Chapter Paging With In-Chapter Paging

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Verify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageBoundaryResolver.kt`
- Verify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderActiveChapterResolver.kt`

- [ ] **Step 1: Make page restore depend on the currently loaded chapter content, not stale previous content**
  Introduce or keep chapter-index-aware loaded state so page restore never uses the wrong chapter’s slices.

- [ ] **Step 2: Keep chapter-boundary destinations semantically correct**
  Forward boundary must land on the next chapter first page. Backward boundary must land on the previous chapter last page.

- [ ] **Step 3: Route cross-chapter paging through the same visible horizontal page-turn flow**
  Avoid abrupt content swaps that feel like a hard cut. The destination chapter should be prepared before the page turn resolves.

- [ ] **Step 4: Re-run the focused page-mode instrumentation tests and verify they pass**

Run:
```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest#pageModeKeepsImmersiveChromeAndRestoresLastChapter,\
com.longerlsx.storyapp.feature.reader.ReaderPageModeTest#pagingBackwardAcrossBoundaryOpensPreviousChapterNearItsEnd
```

Expected: PASS with smooth cross-chapter behavior and correct restore semantics.

## Task 4: Regression And Install

**Files:**
- Verify existing reader helpers and pagination code still behave correctly

- [ ] **Step 1: Run the focused reader unit tests**

Run:
```bash
./gradlew testDebugUnitTest \
  --tests '*ReaderActiveChapterResolverTest' \
  --tests '*ReaderPageBoundaryResolverTest' \
  --tests '*ReaderScrollChapterResolverTest' \
  --tests '*ReaderScrollFeedAnchorMapperTest' \
  --tests '*ReaderPageLinePaginatorTest'
```

Expected: PASS

- [ ] **Step 2: Run the key user-facing reader instrumentation checks**

Run:
```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest#pageModeKeepsImmersiveChromeAndRestoresLastChapter,\
com.longerlsx.storyapp.feature.reader.ReaderPageModeTest#pagingBackwardAcrossBoundaryOpensPreviousChapterNearItsEnd,\
com.longerlsx.storyapp.feature.reader.ReaderChromeTest#centerTapRevealsTwoLayerReaderChrome,\
com.longerlsx.storyapp.feature.reader.ReaderChromeTest#directorySelectionReturnsToReadingOnly,\
com.longerlsx.storyapp.feature.reader.ReaderContinuousScrollTest#scrollModeCanReachNextChapterWithoutExplicitChapterButtons
```

Expected: PASS

- [ ] **Step 3: Install the latest debug build on the emulator**

Run:
```bash
./gradlew installDebug
```

Expected: `Installed on 1 device.`
