# Android Reader Development Cycle

This document is the human-readable companion to the machine-readable bug records in [docs/knowledge/bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md).

It summarizes the main implementation phases from the current Android reader build-out so a new thread can understand what happened without replaying the whole conversation.

## Phase Summary

### 2026-04-15: Foundation and scope lock

- Locked product scope around a local-first Android TXT reader with online-source extension points only.
- Wrote the first architecture and data-flow specs.
- Bootstrapped the Android project, app shell, and baseline test setup.

Related documents:

- [2026-04-15-android-reader-design.md](/Users/longshengxi/proj/story_app/docs/superpowers/specs/2026-04-15-android-reader-design.md)
- [2026-04-15-android-reader-foundation.md](/Users/longshengxi/proj/story_app/docs/superpowers/plans/2026-04-15-android-reader-foundation.md)

### 2026-04-16: Import, parsing, bookshelf, and baseline reader

- Implemented TXT import, private-file copy, hashing, metadata extraction, and chapter parsing.
- Added bookshelf entry flow and first readable正文 screen.
- Stabilized precise reading-position restore.
- Fixed chapter parser over-match and TOC boundary bugs.

Key bugs:

- [BUG-2026-001](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-001-reading-anchor-restore.json)
- [BUG-2026-002](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-002-chapter-parser-overmatch.json)

Related documents:

- [2026-04-16-reader-controls-settings.md](/Users/longshengxi/proj/story_app/docs/superpowers/plans/2026-04-16-reader-controls-settings.md)

### 2026-04-16 to 2026-04-17: Reader controls, immersive chrome, and dual reading modes

- Added settings, themes, day/night toggle, TOC, chapter navigation, and persistent progress handling.
- Introduced scroll mode and page mode as two reader behaviors on shared content data.
- Refined continuous scroll, persistent header behavior, and chapter-boundary semantics.

Key bugs:

- [BUG-2026-003](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-003-page-boundary-previous-chapter.json)
- [BUG-2026-004](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-004-scroll-header-overlap.json)
- [BUG-2026-005](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-005-toc-theme-mismatch.json)
- [BUG-2026-006](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-006-day-night-brightness-coupling.json)

Related documents:

- [2026-04-16-reader-interaction-refresh-design.md](/Users/longshengxi/proj/story_app/docs/superpowers/specs/2026-04-16-reader-interaction-refresh-design.md)
- [2026-04-17-reader-mode-behavior-refine-design.md](/Users/longshengxi/proj/story_app/docs/superpowers/specs/2026-04-17-reader-mode-behavior-refine-design.md)
- [2026-04-16-reader-immersive-page-mode.md](/Users/longshengxi/proj/story_app/docs/superpowers/plans/2026-04-16-reader-immersive-page-mode.md)
- [2026-04-17-reader-mode-behavior-refine.md](/Users/longshengxi/proj/story_app/docs/superpowers/plans/2026-04-17-reader-mode-behavior-refine.md)

### 2026-04-20: Top bar polish and page-mode debugging

- Split immersive header and full chrome top bar into distinct UI states.
- Smoothed cross-chapter page transitions to feel closer to normal page turns.
- Switched default opening behavior toward page mode.
- Resolved a page-mode bottom clipping defect that did not reproduce in scroll mode by aligning page safe-area handling and page text layout assumptions.

Resolved bugs:

- [BUG-2026-007](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-007-cross-chapter-hard-cut.json)
- [BUG-2026-008](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-008-page-mode-bottom-clipping.json)
- [BUG-2026-009](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-009-header-state-mismatch.json)
- [BUG-2026-010](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-010-chrome-auto-hide-too-fast.json)

Related documents:

- [2026-04-20-reader-topbar-and-page-transition-design.md](/Users/longshengxi/proj/story_app/docs/superpowers/specs/2026-04-20-reader-topbar-and-page-transition-design.md)
- [2026-04-20-reader-topbar-and-page-transition.md](/Users/longshengxi/proj/story_app/docs/superpowers/plans/2026-04-20-reader-topbar-and-page-transition.md)

## How Future Threads Should Use This

1. Read [docs/knowledge/bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md) first.
2. If the issue smells similar, open the matching JSON record and compare `symptom`, `root_cause`, and `failed_attempts`.
3. Only after that, jump into implementation files or test code.
4. If a new issue is resolved, add a new JSON record instead of burying the knowledge in conversation history.
