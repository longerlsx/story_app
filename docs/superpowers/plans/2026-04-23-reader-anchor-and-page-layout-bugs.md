# Reader Anchor And Page Layout Bugfixes Implementation Plan

> **管理状态：已关闭、已归档；由后续版本接替（2026-09-22）。** 阅读锚点、分页与段距问题由[第一阶段阅读稳定](2026-09-11-reader-stability-phase-one.md)及[阅读性能与交互优化](2026-09-14-pagination-measurement-reuse.md)接续处理；页顶起读与跟读接线的最新证据见[听书交互实施计划](2026-09-22-zipvoice-listening-upgrade.md)。本旧计划不再单独推进，当前实现与验证边界以[当前状态](../../current-state.md)为准。
>
> 以下保留原方案、工作区备注与勾选状态，未勾选项不再代表当前待办；不补记当时未经证实的执行结果。旧工具要求、命令和协作流程仅属历史记录，不作为新任务指令。

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three reader correctness bugs so scroll-mode TTS starts and follows the visible body text, page-mode long-press restart lands on the pressed visible text, and page-mode paragraph spacing affects both pagination and rendering.

**Architecture:** Treat this as a reader-coordinate bugfix, not a TTS-service change. Split scroll mode and page mode into two independent fixes: scroll mode must stop using whole-item geometry for body anchors, while page mode must adopt a visible-text-aware page slice model so page-top start, long-press hit testing, highlight, and paragraph spacing all share one consistent offset system. Keep `ReaderTtsController` and service behavior unchanged; only repair the reader-side mapping, pagination, and rendering pipeline.

**Tech Stack:** Kotlin, Jetpack Compose, `TextMeasurer`, `LazyColumn`, `HorizontalPager`, Coroutines/Flow, JUnit, Android instrumented tests

---

## Notes

- The current worktree already has unrelated uncommitted changes in:
  - `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
  - `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsSettingsExpansionTest.kt`
- Do **not** fold those files into this bugfix unless a later implementation step proves it is strictly necessary.
- The highest-risk regression area is reader position trustworthiness. If a fix changes visible layout, it must also re-verify:
  - saved reading progress
  - “从当前最上面一行开始读”
  - long-press restart
  - active highlight/follow alignment

## File Map

- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScrollFeedAnchorMapper.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScrollChapterResolver.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageAnchorMapper.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageTextLayout.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderParagraphModel.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageSliceBuilder.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderScrollFeedAnchorMapperTest.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderPageSliceBuilderTest.kt`
- Modify: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocatorTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`

## Task 1: Fix Scroll-Mode Body Anchor Geometry

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScrollFeedAnchorMapper.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScrollChapterResolver.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderScrollFeedAnchorMapperTest.kt`
- Modify: `app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocatorTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt`

- [ ] **Step 1: Write failing unit tests for body-only anchor mapping**

Cover:

- the top visible location must ignore chapter title, author block, spacer, and container padding
- converting `bodyOffsetPx/bodyHeightPx -> charOffset` and `charOffset -> bodyScrollOffsetPx` must not use whole-item height
- `resolveScrollTopLocation()` must return the first visible **body** character, not a position earlier than the visible正文

Suggested tests:

```kotlin
@Test
fun toCharOffset_ignoresHeaderAndPaddingGeometry() { ... }

@Test
fun resolveScrollTopLocation_usesBodyMetricsInsteadOfWholeItemMetrics() { ... }
```

Run:

- `./gradlew testDebugUnitTest --tests '*ReaderScrollFeedAnchorMapperTest'`
- `./gradlew testDebugUnitTest --tests '*ReaderTextStartLocatorTest'`

Expected: FAIL because `ReaderVisibleChapterItem` and the mapper still model the whole chapter item as if it were all正文.

- [ ] **Step 2: Introduce explicit scroll body geometry**

Extend the scroll visible-item model so reader math stops guessing:

- keep whole-item `offsetPx/sizePx` only for list bookkeeping
- add body-specific geometry such as:
  - `bodyOffsetPx`
  - `bodyHeightPx`
- document that all TTS start/follow/save calculations must consume body geometry, not item geometry

- [ ] **Step 3: Feed real body geometry from `ScrollReaderContent`**

Measure the正文 container, not just the surrounding item. The implementation should cache per-chapter body layout metrics and merge them with `LazyListState.layoutInfo.visibleItemsInfo` before calling:

- `saveScrollProgress()`
- `updateReaderSettings(...)` before it persists the current scroll anchor
- `buildTtsStartRequest()`
- scroll-mode auto-follow
- `ReaderTextStartLocator.resolveScrollTopLocation()`

Do not hard-code a fixed “header height”; first chapter and later chapters have different non-body sections.

The cache invalidation rules must be explicit. Recompute or clear cached body geometry whenever any layout-affecting input changes, including at minimum:

- chapter text changes
- chapter order/feed changes
- `fontSizeSp`
- `lineHeightMultiplier`
- `paragraphSpacingEm`
- viewport width/height changes
- safe-drawing inset changes
- orientation / split-screen changes
- reading-mode switches that rebuild the reader surface

Do not let TTS start/follow keep using stale body geometry after a settings change.
The same body-geometry source must also power `updateReaderSettings(...)` and any current-anchor persistence path in `ReaderScreen` so font/line-height updates do not immediately persist a stale pre-fix scroll anchor.

- [ ] **Step 4: Keep the mapper linear within the body region only**

`ReaderScrollFeedAnchorMapper` can remain linear for now, but the linear span must be:

- body text height only
- body top only

Do not attempt paragraph-aware scroll anchoring in this task. Fix the systematic early-start bug first.

- [ ] **Step 5: Add an instrumented regression for visible-top TTS alignment**

Extend `ReaderTtsFollowTest` with a scroll-mode scenario that:

- opens a chapter with author/title/header content above正文
- starts TTS from current location
- verifies the first active range aligns with the currently visible正文 instead of a hidden location above the viewport

Also add a long-press scroll-mode variant so restart from visible正文 remains aligned after the geometry change.

- [ ] **Step 6: Re-run the focused scroll-anchor tests**

Run:

- `./gradlew testDebugUnitTest --tests '*ReaderScrollFeedAnchorMapperTest' --tests '*ReaderTextStartLocatorTest'`
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest`

Expected: PASS

- [ ] **Step 7: Commit the scroll-anchor slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScrollFeedAnchorMapper.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScrollChapterResolver.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderScrollFeedAnchorMapperTest.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocatorTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt
git commit -m "fix: align scroll reader tts anchors with visible body"
```

## Task 2: Introduce Visible-Text-Aware Page Slices

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderParagraphModel.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageSliceBuilder.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageAnchorMapper.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageTextLayout.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderPageSliceBuilderTest.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt`

- [ ] **Step 1: Write failing tests for page-mode visible/raw offset alignment**

Cover:

- when a page starts after leading `\n`, page-top TTS start must land on the first visible character, not the trimmed-away newline
- saving/restoring page progress on a page with trimmed-leading characters must round-trip through the visible page start, not the raw pre-trim start
- page-mode long-press restart must produce a `charOffset` near the pressed visible text
- active highlight must remain aligned to the rendered visible text when the page slice trims or shifts leading characters

Suggested tests:

```kotlin
@Test
fun pageSlice_preservesVisibleStartOffsetAfterLeadingNewlineTrim() { ... }

@Test
fun pageModeLongPressRestart_targetsPressedVisibleText() { ... }

@Test
fun anchorForPageIndex_usesVisibleStartOffsetForTrimmedPage() { ... }
```

Run:

- `./gradlew testDebugUnitTest --tests '*ReaderPageSliceBuilderTest'`
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest`

Expected: FAIL because current page slices only carry raw `startCharOffset/endCharOffset/text`, while rendering and hit-testing use a display string that can be trimmed away from those raw bounds.

- [ ] **Step 2: Extract a shared paragraph model out of `ReaderScreen`**

Move the current private paragraph parsing out of `ReaderScreen.kt` into a reusable model file. The shared paragraph unit must retain original chapter offsets:

- `text`
- `startCharOffset`
- `endCharOffset`

This model becomes the source for both page pagination and page-mode long-press hit testing.

- [ ] **Step 3: Replace raw page slices with visible-text-aware page slices**

Refactor the current page slice model so it can represent the text actually rendered on screen and its original offsets separately. At minimum, the page model needs:

- visible start offset
- visible end offset
- rendered text or rendered paragraph list
- original/raw coverage if needed for page lookup

Do not keep using one `text` field whose display string no longer matches its offsets.

- [ ] **Step 4: Use visible offsets for page-top start, highlight, and long-press**

After the new slice model exists:

- `ReaderPageAnchorMapper.anchorForPageIndex()` must return the visible page start, not the raw pre-trim start
- `savePageProgress()` + restore flow must round-trip through the visible page start on trimmed-leading-character pages
- page-mode long-press restart must resolve from the rendered visible content
- page-mode highlight must style the same visible offsets that the user actually sees

This task should repair the “scroll mode works, page mode long-press fails” divergence without changing the TTS service/controller.

- [ ] **Step 5: Re-run the page-slice and page-restart regressions**

Run:

- `./gradlew testDebugUnitTest --tests '*ReaderPageSliceBuilderTest'`
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest`

Expected: PASS

- [ ] **Step 6: Commit the page-visible-offset slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderParagraphModel.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageSliceBuilder.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageAnchorMapper.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageTextLayout.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/tts/ReaderTextStartLocator.kt \
  app/src/test/java/com/longerlsx/storyapp/feature/reader/ReaderPageSliceBuilderTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt
git commit -m "fix: align page reader visible offsets and restart hit testing"
```

## Task 3: Make Paragraph Spacing First-Class In Page Mode

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageSliceBuilder.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageAnchorMapper.kt`
- Modify: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt`

- [ ] **Step 1: Write failing tests for paragraph spacing in page mode**

Cover:

- changing `paragraphSpacingEm` in page mode changes visible paragraph gap
- the new spacing also influences pagination, not just rendering
- page anchors and chapter restore still land on the expected page after spacing changes

Suggested instrumentation scenario:

- import a chapter with multiple short paragraphs
- switch to page mode
- record current page/visible content at a baseline spacing
- increase paragraph spacing
- assert the page presentation changes in a way that proves the paginator consumed spacing, not just the renderer

Run:

- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest`

Expected: FAIL because page mode currently paginates and renders as if paragraph spacing were always zero.

- [ ] **Step 2: Thread `paragraphSpacing` into the page slice builder**

The paginator must treat spacing as real vertical cost between adjacent paragraphs. It is not enough to apply spacing only at render time.

Implementation rules:

- page fit calculation must count paragraph spacing between rendered paragraphs
- last paragraph on a page should not pay a trailing spacing cost below itself
- if spacing grows, page boundaries may shift; that is expected
- the `remember(...)` keys that build `safePages` and any state path that mirrors those pages into `currentPages` must include `paragraphSpacing`
- `currentPages` and `safePages` must rebuild immediately when `paragraphSpacingEm` changes
- any restore/follow path that reads `currentPages` or `safePages` must observe the rebuilt slices before calling `savePageProgress()`, restore, or follow logic

- [ ] **Step 3: Render page content through paragraph-aware slices**

Render page mode using the same paragraph semantics as scroll mode:

- each visible paragraph keeps original offsets
- visual spacing comes from `paragraphSpacing`
- long-press hit testing stays attached to visible paragraph text, not a synthetic whole-page container

This task should not re-introduce the offset mismatch fixed in Task 2.
It must also make the rebuild behavior explicit: changing `paragraphSpacingEm` while staying on the same chapter must regenerate `currentPages` and `safePages` immediately so `savePageProgress()`, restore, and follow all observe the new pages without requiring a chapter reopen.

- [ ] **Step 4: Re-run page-mode regression tests**

Run:

- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest`

Expected: PASS

- [ ] **Step 5: Commit the page-spacing slice**

Run:
```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageSliceBuilder.kt \
  app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderPageAnchorMapper.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderPageModeTest.kt \
  app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderTtsFollowTest.kt
git commit -m "fix: honor paragraph spacing in page reader"
```

## Task 4: Run Full Reader Regression And Hand Off

**Files:**
- Modify: `docs/superpowers/plans/2026-04-23-reader-anchor-and-page-layout-bugs.md`

- [ ] **Step 1: Re-run the full reader unit test set**

Run:

- `./gradlew testDebugUnitTest`

Expected: PASS

- [ ] **Step 2: Re-run the targeted reader instrumentation regression**

Run:

- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderPageModeTest,com.longerlsx.storyapp.feature.reader.ReaderTtsFollowTest,com.longerlsx.storyapp.feature.reader.ReaderContinuousScrollTest,com.longerlsx.storyapp.feature.reader.ReaderSettingsPersistenceTest`

Expected: PASS

- [ ] **Step 3: Fresh-install the debug build for manual verification**

Run:

- `./gradlew installDebug`

Expected: PASS, with the newest build installed on the test emulator/device.

- [ ] **Step 4: Perform manual smoke checks**

Validate:

- scroll mode: from-current-position TTS starts on the visible top正文
- scroll mode: long-press restart lands on the visible pressed paragraph
- page mode: long-press restart lands on the pressed visible text
- page mode: paragraph spacing change is visible and changes pagination
- page mode: active highlight still tracks the visible spoken text

- [ ] **Step 5: Update plan checkboxes and prepare review handoff**

Record actual completion state in this plan file, then request a final code review before merge.
