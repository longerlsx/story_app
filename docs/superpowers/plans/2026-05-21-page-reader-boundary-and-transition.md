# Page Reader Boundary And Transition Fix Implementation Plan

> **管理状态：已关闭、已归档；由后续版本接替（2026-09-22）。** 章末点击无响应与跨章文字跳动已在[BUG-2026-011](../../knowledge/bugs/BUG-2026-011-page-boundary-pager-state.json)和[BUG-2026-012](../../knowledge/bugs/BUG-2026-012-boundary-transition-layout-mismatch.json)记录后续解决；旧分章Pager及独立过渡动画已被统一分页窗口替代。后续实现与回归见[第一阶段阅读稳定](2026-09-11-reader-stability-phase-one.md)及[阅读性能与交互优化](2026-09-14-pagination-measurement-reuse.md)，当前结论以[当前状态](../../current-state.md)为准，本旧计划不再单独推进。
>
> 以下保留当时勾选状态，未勾选的旧手工检查不再作为当前待办，也不补记为当时已经通过。旧工具要求、命令、审查轮次及临时路径仅属历史记录，不作为新任务指令或当前可用证据。

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix page-mode right-edge chapter-boundary taps and remove the visible re-layout jump during cross-chapter page transitions.

**Architecture:** Treat this as a page-reader state and rendering consistency fix. Keep pagination logic intact, but isolate pager state per chapter, make boundary taps depend on settled/restored page state, pass explicit target chapter indexes through boundary callbacks, and render boundary transition pages through the exact same page surface used by the real pager.

**Tech Stack:** Kotlin, Jetpack Compose, Foundation Pager, Android instrumented tests, JUnit

---

## Subagent Review Summary

- Boundary tap review: `PagerState` is remembered across chapter changes while `safePages` changes; right-edge tap uses `pagerState.currentPage < safePages.lastIndex`, so it can take the wrong branch during restore/content transitions. Use per-chapter pager state, settled page, loaded/restored gating, and explicit boundary targets.
- Transition rendering review: boundary transition stores `String` text and draws it with one raw `Text`, while the final page uses `ReaderPageParagraphContent` and paragraph spacing. Store `ReaderPageSlice` in the transition and draw boundary layers through the same page surface as `HorizontalPager`.

## File Map

- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageBoundaryResolver.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderPageBoundaryResolverTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageParagraphSpacingTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt`
- Update: `docs/knowledge/bugs/INDEX.md`
- Create: `docs/knowledge/bugs/BUG-2026-011-page-boundary-pager-state.json`
- Create: `docs/knowledge/bugs/BUG-2026-012-boundary-transition-layout-mismatch.json`

## Task 1: Lock Down Forward Boundary Tap Regression

**Files:**
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`
- Optionally reuse helpers from `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderUiDeviceHelpers.kt`

- [x] **Step 1: Add failing UI test for right-edge boundary turn**

Add `pagingForwardAcrossBoundaryByRightTapOpensNextChapter`:
- import a two-chapter txt
- first chapter must have enough body text to paginate to multiple pages
- second chapter must contain a unique first-page marker
- switch to page mode
- tap the right reading area until the first chapter end marker is visible
- tap the right reading area once more
- assert the second chapter marker appears

- [x] **Step 2: Run the focused test and verify failure**

Run:
```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest
```

Expected before the fix: the new test should be flaky or fail on the right-edge boundary path.

## Task 2: Make Page Boundary Taps Use Stable Page State

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageBoundaryResolver.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderPageBoundaryResolverTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt`

- [x] **Step 1: Add pure boundary-turn resolver coverage**

Extend `ReaderPageBoundaryResolver` with a small pure decision helper for previous/next page turns. Add unit tests for:
- ignoring turns before content is loaded/restored or while pager scroll is active
- turning inside the current chapter from the settled page
- opening explicit previous/next target chapters at page boundaries

- [x] **Step 2: Key pager state by chapter**

In `PageReaderContent`, isolate pager-adjacent state by `bookId` and `chapterIndex`. Use `key(bookId, chapterIndex)` or an equivalent local composable split so these states do not leak across chapters:
- `pagerState`
- `lastSettledPage`
- `pendingProgrammaticSettledPage`

Keep chapter content, page slicing, and boundary preview data outside only where they are purely derived.

- [x] **Step 3: Gate left/right page taps until content is usable**

In the body `detectTapGestures`, allow center-tap chrome behavior as today, but ignore previous/next page turns unless:
- `contentLoaded == true`
- `restoredPosition == true`
- `safePages.isNotEmpty()`
- `pagerState.isScrollInProgress == false`
- `boundaryTransition == null`

- [x] **Step 4: Use settled page for boundary decisions**

Replace `pagerState.currentPage` boundary checks with:
```kotlin
val activePage = pagerState.settledPage.coerceIn(0, safePages.lastIndex)
```

For right tap:
- if `activePage < safePages.lastIndex`, animate to `activePage + 1`
- else open `nextChapterIndex`

For left tap:
- if `activePage > 0`, animate to `activePage - 1`
- else open `previousChapterIndex`

- [x] **Step 5: Pass explicit target chapter indexes through boundary callbacks**

Change callbacks to carry the target:
```kotlin
onOpenPreviousBoundary: (targetChapterIndex: Int) -> Unit
onOpenNextBoundary: (targetChapterIndex: Int) -> Unit
```

Parent handlers should call `openChapter(target, ...)` directly. Previous boundary keeps `restoreToLastPage = true`; next boundary opens at chapter start.

- [x] **Step 6: Re-run boundary and TTS stop regressions**

Run:
```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest
```

Expected: right-edge boundary opens next chapter, backward boundary still opens previous chapter near its end, active TTS is still stopped before boundary navigation.

## Task 3: Render Boundary Transition With The Real Page Surface

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageParagraphSpacingTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`

- [x] **Step 1: Extract a shared page surface composable**

Create a private composable in `ReaderScreen.kt`, for example `ReaderPageSurface`, responsible for:
- `fillMaxSize`
- horizontal/top/bottom page padding
- `ReaderPageParagraphContent`
- optional highlight and interaction callbacks

`HorizontalPager` should render pages through this surface.

- [x] **Step 2: Store page slices in boundary transition state**

Change `ReaderBoundaryPageTransition` from string fields:
```kotlin
sourceText: String
previewText: String
```

to:
```kotlin
sourcePage: ReaderPageSlice
previewPage: ReaderPageSlice
```

Use `safePages.getOrNull(activePage)`, `previousPages.lastOrNull()`, and `nextPages.firstOrNull()`.

- [x] **Step 3: Render boundary layers through the shared page surface**

Change `ReaderBoundaryPageLayer` to accept a `ReaderPageSlice` and call `ReaderPageSurface` with:
- the same `themePalette`
- the same `fontSize`
- the same `lineHeight`
- the same `paragraphSpacing`
- the same `pageTopPadding`
- the same `pageBottomPadding`

Do not enable long-press, tap-to-toggle, or live TTS highlight on transition overlay layers unless a later feature explicitly needs it.

- [x] **Step 4: Add a regression around high paragraph spacing**

Add or extend an instrumented test with:
- page mode
- high `paragraphSpacingEm`
- cross-chapter boundary turn
- next/previous preview page containing multiple paragraphs and blank lines

The assertion can focus on stable visible markers after transition, plus manual verification notes if exact pixel baseline checks are not practical in existing test infrastructure.

- [x] **Step 5: Re-run page rendering tests**

Run:
```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReaderPageParagraphSpacingTest
```

Expected: paragraph spacing still changes pagination, and cross-chapter turns no longer swap from raw `Text` layout to paragraph-aware layout at the end.

## Task 4: Knowledge Records And Full Verification

**Files:**
- Create: `docs/knowledge/bugs/BUG-2026-011-page-boundary-pager-state.json`
- Create: `docs/knowledge/bugs/BUG-2026-012-boundary-transition-layout-mismatch.json`
- Modify: `docs/knowledge/bugs/INDEX.md`

- [x] **Step 1: Record the two bugs**

Create bug records:
- `BUG-2026-011`: page-mode right-edge boundary tap can no-op because pager state leaks across chapter/content transitions.
- `BUG-2026-012`: cross-chapter transition preview and final page use different renderers.

- [x] **Step 2: Run focused unit tests**

Run:
```bash
./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.feature.reader.ReaderPageAnchorMapperTest --tests com.longerlsx.storyapp.feature.reader.ReaderPageBoundaryResolverTest --tests com.longerlsx.storyapp.feature.reader.ReaderPageLinePaginatorTest --tests com.longerlsx.storyapp.feature.reader.ReaderPageFollowTargetResolverTest
```

Expected: PASS.

- [x] **Step 3: Run focused instrumented tests**

Run:
```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReaderPageParagraphSpacingTest,com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest
```

Expected: PASS.

- [ ] **Step 4: Manual verification**

On device/emulator:
- open a multi-page chapter in page mode
- tap right area to the last page
- tap right area again and confirm next chapter opens
- set paragraph spacing high
- cross a chapter boundary forward and backward
- confirm there is no visible text baseline jump when the boundary animation finishes

Note: full hand-check matrix still not run as a separate manual pass. Current verification includes the focused instrumented suite, Computer Use confirmation that the Pixel 8 API 34 emulator was booted and visible in Android Studio, and an emulator screen recording of `ReaderPageModeTest#pagingForwardAcrossBoundaryByRightTapOpensNextChapter` saved at `/private/tmp/story_app_verification/reader_boundary_forward.mp4`.

## Task 5: Post-Implementation Review Gate

**Files:**
- Review: all changed code and tests

- [x] **Step 1: Launch code-review subagent**

After code writing and local focused tests, open a subagent whose only job is code review:
- inspect the diff against this plan
- prioritize behavioral regressions, lifecycle/state bugs, and Compose pager misuse
- return findings by severity with file/line references

- [x] **Step 2: Launch test-completeness subagent**

Open a second subagent whose only job is test completeness review:
- inspect the unit and instrumented tests added or changed
- identify missing coverage for the two reported bugs and adjacent regressions
- distinguish mandatory gaps from acceptable manual-verification gaps

- [x] **Step 3: Integrate review findings and re-run verification**

Fix valid Critical/Important findings from either subagent, then re-run the focused unit and instrumented test commands before reporting final root cause and fix summary.
