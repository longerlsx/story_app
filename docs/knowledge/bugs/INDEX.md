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

## Retrieval Hints For Future Threads

- If a new issue mentions “same chapter but wrong place”, start with `BUG-2026-001`.
- If page turns feel wrong only at chapter edges, compare `BUG-2026-003` and `BUG-2026-007`.
- If the last line of page mode looks visually obstructed, start with `BUG-2026-008`; the confirmed fix was to align safe drawing insets and page `TextStyle` between measurement and rendering, and not to repeat its failed attempts first.
- If a styling complaint only affects night mode overlays or sheets, compare `BUG-2026-005`.

## Authoring Rules

- Do not write chat-style debugging diaries.
- Prefer abstract, reusable root causes over one-off wording.
- Always fill `symptom`, `root_cause`, `fix`, and `tags`.
- Add at least one state tag such as `resolved` or `investigating`.
- Keep `failed_attempts` only for approaches that future threads should avoid repeating.
