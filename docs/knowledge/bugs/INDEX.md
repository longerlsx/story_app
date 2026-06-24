# Bug Knowledge Base

This directory stores structured bug records for the Android reader project. Each bug is recorded as one JSON file so future threads can:

- search by `tags`
- compare `symptom` to a new issue
- jump directly to `root_cause`
- avoid repeating `failed_attempts`

## Schema

Each record follows the same core shape:

```json
{
  "id": "BUG-2026-001",
  "title": "",
  "symptom": "",
  "root_cause": "",
  "fix": [],
  "failed_attempts": [],
  "impact": "",
  "tags": [],
  "confidence": 0.8,
  "created_at": "",
  "updated_at": ""
}
```

Canonical template:

- [TEMPLATE.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/TEMPLATE.json)

## Required Workflow

Every debugging or bug-fix thread must follow this update path before the task is considered complete:

1. Create a new bug JSON if the issue is new.
2. Update an existing bug JSON if the issue is a continuation, refinement, or newly understood root cause.
3. Record failed-but-reusable attempts in `failed_attempts` instead of burying them in chat history.
4. Add or update the row in the `Records` table below.
5. If the bug affects broader project understanding, update the cycle summary in [../2026-04-android-reader-development-cycle.md](/Users/longshengxi/proj/story_app/docs/knowledge/2026-04-android-reader-development-cycle.md).

This is mandatory for both:

- successfully resolved bugs
- high-value ongoing investigations

## Tag Conventions

- Platform: `android`, `compose`
- Domain: `reader`, `txt`, `toc`, `settings`
- Behavior: `page-mode`, `scroll-mode`, `anchor-restore`, `chapter-boundary`, `window-insets`, `text-layout`
- State: `resolved`, `investigating`, `failed-attempts`

## Records

| ID | Title | Status | Key Tags | File |
| --- | --- | --- | --- | --- |
| BUG-2026-001 | Reader restart restores the chapter but loses the exact intra-chapter position | resolved | `reader`, `reading-progress`, `anchor-restore` | [BUG-2026-001-reading-anchor-restore.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-001-reading-anchor-restore.json) |
| BUG-2026-002 | TXT chapter parser over-matches body text that looks like a chapter title | resolved | `txt`, `chapter-parser`, `toc` | [BUG-2026-002-chapter-parser-overmatch.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-002-chapter-parser-overmatch.json) |
| BUG-2026-003 | Paging backward across a chapter boundary opens the previous chapter at the start instead of the end | resolved | `page-mode`, `chapter-boundary` | [BUG-2026-003-page-boundary-previous-chapter.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-003-page-boundary-previous-chapter.json) |
| BUG-2026-004 | Scroll-mode immersive header overlaps the first visible lines of text | resolved | `scroll-mode`, `immersive-header` | [BUG-2026-004-scroll-header-overlap.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-004-scroll-header-overlap.json) |
| BUG-2026-005 | Night-mode TOC styling does not follow the active reader theme | resolved | `toc`, `night-mode`, `theme` | [BUG-2026-005-toc-theme-mismatch.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-005-toc-theme-mismatch.json) |
| BUG-2026-006 | Day mode and night mode incorrectly share one brightness value | resolved | `brightness`, `settings`, `day-night` | [BUG-2026-006-day-night-brightness-coupling.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-006-day-night-brightness-coupling.json) |
| BUG-2026-007 | Cross-chapter paging feels like a hard cut instead of a continuous page turn | resolved | `page-mode`, `animation`, `chapter-boundary` | [BUG-2026-007-cross-chapter-hard-cut.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-007-cross-chapter-hard-cut.json) |
| BUG-2026-008 | Page mode clips the last visible line near the bottom edge while scroll mode remains correct | resolved | `page-mode`, `window-insets`, `text-layout` | [BUG-2026-008-page-mode-bottom-clipping.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-008-page-mode-bottom-clipping.json) |
| BUG-2026-009 | Immersive header and chrome-visible top bar used the wrong visual treatment | resolved | `top-bar`, `immersive-mode`, `ux` | [BUG-2026-009-header-state-mismatch.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-009-header-state-mismatch.json) |
| BUG-2026-010 | Reader chrome auto-hides too quickly for real settings interaction | resolved | `chrome`, `timeout`, `ux` | [BUG-2026-010-chrome-auto-hide-too-fast.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-010-chrome-auto-hide-too-fast.json) |
| BUG-2026-011 | Page-mode right-edge chapter-boundary tap can no-op at the end of a chapter | resolved | `page-mode`, `chapter-boundary`, `pager-state` | [BUG-2026-011-page-boundary-pager-state.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-011-page-boundary-pager-state.json) |
| BUG-2026-012 | Cross-chapter page transition preview reflows when the real chapter page mounts | resolved | `page-mode`, `chapter-boundary`, `text-layout` | [BUG-2026-012-boundary-transition-layout-mismatch.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-012-boundary-transition-layout-mismatch.json) |
| BUG-2026-013 | TXT chapter parser misses spaced numeric headings and leaks double-title lines into chapter bodies | resolved | `txt`, `toc`, `chapter-parser` | [BUG-2026-013-txt-chapter-parser-legado-inspired.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-013-txt-chapter-parser-legado-inspired.json) |
| BUG-2026-014 | Reader settings controls leak Material default colors and show orphan static labels | resolved | `settings`, `night-mode`, `theme`, `visual` | [BUG-2026-014-reader-settings-slider-theme-leak.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-014-reader-settings-slider-theme-leak.json) |
| BUG-2026-015 | Reader UIAutomator helpers mis-tap non-clickable semantics nodes and leak chrome state | resolved | `uiautomator`, `chrome`, `testing`, `interaction` | [BUG-2026-015-reader-uiautomator-click-helper-state-leak.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-015-reader-uiautomator-click-helper-state-leak.json) |
| BUG-2026-016 | Timed TTS bottom-bar labels wrap and inflate the reader chrome | resolved | `tts`, `chrome`, `visual` | [BUG-2026-016-reader-tts-timer-label-wrap.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-016-reader-tts-timer-label-wrap.json) |
| BUG-2026-017 | TXT parser rejects real chapter titles that end with punctuation after subtitle separators | resolved | `txt`, `toc`, `chapter-parser`, `offset` | [BUG-2026-017-txt-parser-subtitle-punctuation-headings.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-017-txt-parser-subtitle-punctuation-headings.json) |
| BUG-2026-018 | Page-mode progress persistence uses raw page offsets instead of visible page starts | resolved | `reader`, `page-mode`, `anchor-restore`, `offset` | [BUG-2026-018-page-mode-progress-uses-raw-page-offset.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-018-page-mode-progress-uses-raw-page-offset.json) |
| BUG-2026-019 | Reader TTS timer state can leak from another book into the current reader settings UI | resolved | `reader`, `tts`, `settings`, `visual` | [BUG-2026-019-reader-tts-timer-state-cross-book-leak.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-019-reader-tts-timer-state-cross-book-leak.json) |
| BUG-2026-020 | Reader TTS settings sheet exposes developer-facing mixed English copy | resolved | `reader`, `tts`, `settings`, `visual` | [BUG-2026-020-reader-tts-settings-developer-copy.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-020-reader-tts-settings-developer-copy.json) |
| BUG-2026-021 | Reader TTS settings tab treats voice loading as no available mainland voices | resolved | `reader`, `tts`, `settings`, `visual` | [BUG-2026-021-reader-tts-voice-loading-empty-state.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-021-reader-tts-voice-loading-empty-state.json) |
| BUG-2026-022 | Reader TTS can briefly show a 0m countdown label when the timer expires | resolved | `reader`, `tts`, `timer`, `notification`, `visual` | [BUG-2026-022-reader-tts-expired-timer-zero-minute-label.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-022-reader-tts-expired-timer-zero-minute-label.json) |
| BUG-2026-023 | Reader chapter progress counts synthetic preface as a numbered chapter | resolved | `reader`, `txt`, `visual`, `progress` | [BUG-2026-023-reader-progress-counts-synthetic-preface.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-023-reader-progress-counts-synthetic-preface.json) |
| BUG-2026-024 | TXT parser rejects numbered subtitle chapter titles that end with punctuation | resolved | `txt`, `toc`, `chapter-parser`, `offset` | [BUG-2026-024-txt-parser-numbered-subtitle-punctuation-headings.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-024-txt-parser-numbered-subtitle-punctuation-headings.json) |
| BUG-2026-025 | TXT parser drops numbered chapter titles when a repeated special subtitle follows | resolved | `txt`, `toc`, `chapter-parser`, `offset` | [BUG-2026-025-txt-parser-special-double-title-merge.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-025-txt-parser-special-double-title-merge.json) |
| BUG-2026-026 | TXT parser treats in-body round attempt lines ending with ellipsis as chapters | resolved | `txt`, `toc`, `chapter-parser`, `chapter-boundary` | [BUG-2026-026-txt-parser-round-punctuation-body-line.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-026-txt-parser-round-punctuation-body-line.json) |
| BUG-2026-027 | External TXT import crashes when a file URI cannot be opened | resolved | `txt`, `external-intent`, `crash` | [BUG-2026-027-external-import-unreadable-file-uri-crash.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-027-external-import-unreadable-file-uri-crash.json) |
| BUG-2026-028 | TXT parser treats chapter-like dialogue lines as real chapter headings | resolved | `txt`, `chapter-parser`, `offset` | [BUG-2026-028-txt-parser-chapter-like-dialogue-false-positive.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-028-txt-parser-chapter-like-dialogue-false-positive.json) |
| BUG-2026-029 | Page-mode body text center taps can miss reader chrome toggle | resolved | `reader`, `page-mode`, `chrome`, `interaction` | [BUG-2026-029-page-mode-body-center-tap-missed.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-029-page-mode-body-center-tap-missed.json) |
| BUG-2026-030 | Bookshelf and source entry expose implementation-progress copy | resolved | `bookshelf`, `source-entry`, `visual`, `copy` | [BUG-2026-030-bookshelf-source-developer-copy.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-030-bookshelf-source-developer-copy.json) |
| BUG-2026-031 | Reader theme swatch borders leak Material surface colors | resolved | `reader`, `settings`, `theme`, `visual` | [BUG-2026-031-reader-theme-swatch-border-theme-leak.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-031-reader-theme-swatch-border-theme-leak.json) |
| BUG-2026-032 | Reader TTS transient message leaks Material surface colors | resolved | `reader`, `tts`, `theme`, `visual` | [BUG-2026-032-reader-tts-transient-message-theme-leak.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-032-reader-tts-transient-message-theme-leak.json) |
| BUG-2026-033 | Reader TTS transient message cleanup can clear another book's local feedback | resolved | `reader`, `tts`, `interaction`, `visual` | [BUG-2026-033-reader-tts-transient-message-cross-book-clear.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-033-reader-tts-transient-message-cross-book-clear.json) |
| BUG-2026-034 | Reader chapter progress summary can wrap and inflate the navigation row | resolved | `reader`, `chrome`, `visual` | [BUG-2026-034-reader-chapter-progress-summary-wraps-navigation-row.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-034-reader-chapter-progress-summary-wraps-navigation-row.json) |
| BUG-2026-035 | GBK-compatible TXT import decodes as UTF-8 and loses Chinese chapters | resolved | `txt`, `import`, `charset`, `chapter-parser` | [BUG-2026-035-gbk-txt-import-garbled-text.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-035-gbk-txt-import-garbled-text.json) |
| BUG-2026-036 | Reader settings tabs scroll away with long TTS settings content | resolved | `reader`, `settings`, `tts`, `interaction`, `visual` | [BUG-2026-036-reader-settings-tabs-scroll-away.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-036-reader-settings-tabs-scroll-away.json) |
| BUG-2026-037 | Reader settings tab body scroll state leaks between Reading and TTS tabs | resolved | `reader`, `settings`, `tts`, `interaction`, `visual` | [BUG-2026-037-reader-settings-tab-scroll-state-leak.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-037-reader-settings-tab-scroll-state-leak.json) |
| BUG-2026-038 | Reader TOC opens long directories at the top instead of the current chapter | resolved | `reader`, `toc`, `interaction`, `visual` | [BUG-2026-038-reader-toc-current-chapter-not-visible.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-038-reader-toc-current-chapter-not-visible.json) |
| BUG-2026-039 | Reader top bar exposes a dead More action | resolved | `reader`, `chrome`, `interaction`, `visual` | [BUG-2026-039-reader-top-bar-dead-more-action.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-039-reader-top-bar-dead-more-action.json) |
| BUG-2026-040 | Page-mode immersive header overlaps the first body line | resolved | `reader`, `page-mode`, `immersive-header`, `visual` | [BUG-2026-040-page-mode-immersive-header-overlaps-body.json](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-040-page-mode-immersive-header-overlaps-body.json) |

## Retrieval Hints For Future Threads

- If a new issue mentions “same chapter but wrong place”, start with `BUG-2026-001`.
- If page turns feel wrong only at chapter edges, compare `BUG-2026-003` and `BUG-2026-007`.
- If right-edge taps at a chapter end sometimes do nothing, start with `BUG-2026-011`; the confirmed fix is to isolate pager state by chapter and use `settledPage`.
- If text jumps when a cross-chapter page animation finishes, start with `BUG-2026-012`; the confirmed fix is to render animation previews through the same page surface as the pager.
- If page mode restores or re-anchors to a position just before the visible top of a page, especially near leading blank lines, start with `BUG-2026-018`; the confirmed fix is to persist `visibleStartCharOffset` from `ReaderPageSlice`.
- If TXT import misses `第 1 章` or leaks adjacent duplicate title lines like `第一章` into body text, start with `BUG-2026-013`; the confirmed fix is the clean-room rule-selection parser and body-offset persistence.
- If a real TXT has chapter titles like `第2章 系列名:一句疑问句？` and parser merges many chapters together, start with `BUG-2026-017`; the confirmed fix is punctuation allowance only after explicit subtitle separators plus an external corpus smoke test.
- If a real TXT has chapter titles like `第1章 001 系统都能绑错？` or `第51章 050 丹道比试，第一！`, start with `BUG-2026-024`; the confirmed fix is a narrow numbered-subtitle punctuation allowance on Chinese numbered chapter rules, not a broad sentence-punctuation exception.
- If a real TXT outputs `番外一` where the source heading is `第141章 番外一` followed by a repeated short `番外一` line, start with `BUG-2026-025`; the confirmed fix is exact-tail `CHAPTER + SPECIAL` double-title merging in `ChapterCandidateMerger`.
- If a real TXT splits a chapter at an in-body retry marker like `第十三回 ……`, start with `BUG-2026-026`; the confirmed fix is a `回`-only pure-punctuation tail guard, not adding ellipsis to the global sentence-ending rejection set.
- If the last line of page mode looks visually obstructed, start with `BUG-2026-008`; the confirmed fix was to align safe drawing insets and page `TextStyle` between measurement and rendering, and not to repeat its failed attempts first.
- If a styling complaint only affects night mode overlays or sheets, compare `BUG-2026-005`.
- If a reader settings or TTS control shows unrelated accent colors or non-interactive labels, start with `BUG-2026-014`; the confirmed fix is to theme reader overlay controls from `ReaderThemePalette` and reuse the same themed control helpers instead of relying on component defaults.
- If the TTS settings tab shows contradictory state like `未朗读` while still showing a countdown, start with `BUG-2026-019`; the confirmed fix is current-book-aware reader-side TTS UI derivation.
- If the TTS settings tab shows mixed English or implementation terms like `TTS engine` / `voice`, start with `BUG-2026-020`; the confirmed fix is user-facing sheet copy plus a Compose text regression.
- If the TTS settings tab says no mainland Chinese voices are available before the system voice catalog has loaded, start with `BUG-2026-021`; the confirmed fix is to track `availableVoicesLoaded` separately from the voice list.
- If TTS timer expiry flashes `0m` in the bottom bar, settings tab, immersive action, or notification, start with `BUG-2026-022`; the confirmed fix is to route live labels through `formatPositiveRemainingMillisOrNull`.
- If UIAutomator reader tests pass alone but fail in mixed groups, logcat says a non-clickable Compose semantics node was clicked, reset helpers delete files but leave app-process state alive, or a page-mode mixed class cannot navigate to its target page, start with `BUG-2026-015`; the confirmed fix is center/shell-coordinate clicks plus helpers that leave chrome and in-memory repositories in a known state.
- If the bottom reader chrome grows, jumps, or crowds after enabling a TTS countdown, start with `BUG-2026-016`; the confirmed fix is single-line shared action-label rendering for long timed TTS labels, and visual regressions should derive timed labels from the current reader TTS UI resolver instead of stale literals.
- If a real TXT opens at `第1章` but the bottom chapter progress shows `2/N章`, start with `BUG-2026-023`; the confirmed fix is to format reader progress through a display helper that excludes a leading synthetic `前言` from numbered chapter counts without changing parser offsets or navigation.
- If an external TXT open/share intent returns to the launcher or force-closes the app, start with `BUG-2026-027`; the confirmed fix is to treat URI query/open failures as a non-importable payload instead of letting `ContentResolver.openInputStream` exceptions escape.
- If a body line that starts like a chapter heading, such as `第1章 她想：开始了吗？` or `第十三回合：还要继续吗？`, appears as a TOC entry, start with `BUG-2026-028`; the confirmed fix is to keep colon-subtitle punctuation support narrow and reject dialogue-like tails.
- If page mode turns pages from right-edge taps but center taps on dense body text do not reveal the reader chrome, start with `BUG-2026-029`; the confirmed fix is to observe tap zones on the `HorizontalPager` gesture path and cover shell-level `input tap`, not only `UiDevice.click`.
- If first-run bookshelf or unavailable source screens show implementation-progress wording such as “已经打通” or “能力已预留”, start with `BUG-2026-030`; the confirmed fix is to keep entry-surface copy user-facing and cover old phrases with Compose text regressions.
- If reader settings color swatches show colors that do not belong to the active reader palette, start with `BUG-2026-031`; the confirmed fix is to render swatch borders from `ReaderThemePalette` and verify surface-role leaks with pixel trap colors.
- If a temporary TTS status/error message over the reader uses colors that do not match the active reader theme, start with `BUG-2026-032`; the confirmed fix is to route `ReaderTransientTtsMessage` through `ReaderThemePalette` and verify the full reader root with Material surface/onSurface trap colors.
- If a temporary TTS status/error message disappears after switching books or after another book produces the same feedback text, start with `BUG-2026-033`; the confirmed fix is to make delayed cleanup current-book-aware instead of clearing by text match alone.
- If the bottom chapter-navigation row grows taller or jumps when the progress summary is long, start with `BUG-2026-034`; the confirmed fix is to constrain the progress text to one line with ellipsis and verify height against a single-line action.
- If importing a Chinese TXT produces unreadable text, missing title/author, or a single fallback body chapter, start with `BUG-2026-035`; the confirmed fix is strict UTF-8 validation before falling back to GB18030 for GBK/GB2312-compatible inputs.
- If the reader settings tab switcher disappears after scrolling through long TTS settings, start with `BUG-2026-036`; the confirmed fix is to keep the tab row outside the scrollable body and verify after scrolling to the bottom timer option.
- If switching between Reading and TTS settings opens the new tab midway down instead of at its top controls, start with `BUG-2026-037`; the confirmed fix is to scope the scroll state per settings tab.
- If a long TOC opens at the first chapters instead of showing the active reading chapter, start with `BUG-2026-038`; the confirmed fix is to initialize the TOC LazyColumn at the selected chapter's list position.
- If the reader top bar shows an action that can be tapped but has no visible result, start with `BUG-2026-039`; the confirmed fix is to hide placeholder actions until a real menu/callback exists and avoid tests depending on dead controls.
- If page-mode first-page text appears pressed into or under the lightweight immersive header, start with `BUG-2026-040`; the confirmed fix is to keep the base page top padding high enough for the measured header band without tying it to temporary chrome-visible top-bar height.

## Authoring Rules

- Do not write chat-style debugging diaries.
- Prefer abstract, reusable root causes over one-off wording.
- Always fill `symptom`, `root_cause`, `fix`, and `tags`.
- Add at least one state tag such as `resolved` or `investigating`.
- Keep `failed_attempts` only for approaches that future threads should avoid repeating.
