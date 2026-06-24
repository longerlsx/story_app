# Agent Operating Agreements

This document records the thread-level operating agreements that must survive
context compaction and future sessions. It is part of the required read path for
reader work.

## Required Read Position

Before working on reader/parser/TTS/settings behavior, read these in order.
This applies to every new work slice and every continuation after context
compaction, not only to a fresh thread:

1. [README.md](/Users/longshengxi/proj/story_app/README.md)
2. [docs/knowledge/README.md](/Users/longshengxi/proj/story_app/docs/knowledge/README.md)
3. This file
4. [docs/knowledge/bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md)
5. Bug JSON records matching the current tags or symptom
6. Relevant specs/plans under [docs/superpowers/specs](/Users/longshengxi/proj/story_app/docs/superpowers/specs) and [docs/superpowers/plans](/Users/longshengxi/proj/story_app/docs/superpowers/plans)

Do not treat this read order as proof of correctness. It only establishes
context and hypotheses.

For long-running goals, the minimum per-slice refresh inside this file is:

1. `Initial Agreement Index`
2. `Current Priority`
3. `Emulator And UI Evidence`
4. `Subagent Use`
5. `Git Cadence`

## Initial Agreement Index

The active project objective is to systematically improve the Story App Android
TXT reader's code structure, module boundaries, reuse, and test coverage while
finding and fixing real usage, visual, and interaction bugs.

Standing agreements:

1. Evidence outranks memory. Current code paths, current command output,
   reproducible behavior, logs, emulator observation, screenshots, recordings,
   and test output are evidence.
2. Historical docs, bug records, plans, test names, and previous conclusions are
   search clues and hypotheses, not final judgment sources.
3. Passing tests are not sufficient proof of user-facing correctness. Final
   claims must cross-check code, current test output, runtime behavior, or
   reproducible steps.
4. Avoid broad, purposeless rewrites. Prefer small, verifiable improvements at
   bug clusters, duplicate logic, inconsistent state flows, and hard-to-test
   boundaries.
5. Prefer reusable pure logic for reader/parser/TTS/settings decisions, covered
   by unit tests. Keep Compose/UI code focused on state connection and rendering.
6. Do not break current behavior. Structural changes need regression tests or
   explicit manual verification evidence.
7. Do not revert user changes. In a dirty worktree, identify unrelated changes
   before editing.
8. Confirmed bug fixes must update [docs/knowledge/bugs](/Users/longshengxi/proj/story_app/docs/knowledge/bugs) and [bugs/INDEX.md](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/INDEX.md).
9. Reusable failed paths belong in `failed_attempts`, not only in chat history.
10. If project-level understanding changes, update
    [2026-04-android-reader-development-cycle.md](/Users/longshengxi/proj/story_app/docs/knowledge/2026-04-android-reader-development-cycle.md).

## Current Priority

As of 2026-06-24, bias new investigation and optimization work toward page mode
unless a higher-priority regression is discovered.

Page-mode work means discovering and reducing visual and interaction defects in
the actual reading experience first. Code structure improvements are useful
when they make page-mode behavior easier to prove, reuse, and maintain; they
are not an end in themselves.

Primary page-mode risk areas:

- page anchor save/restore and visible page starts
- page-mode and scroll-mode consistency where they share state
- cross-chapter forward/backward turns
- page layout, paragraph spacing, line height, font size, safe areas, and bottom clipping
- top bar, bottom chrome, settings sheet, directory, and night-mode overlays while in page mode
- tap zones, center-tap chrome reveal, left/right page turns, long press, and auto-hide timing
- TTS start/follow/highlight/pause behavior across pages and chapter boundaries
- settings changes that should invalidate pagination or layout cache

Start page-mode retrieval from these records when symptoms overlap:

- [BUG-2026-003](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-003-page-boundary-previous-chapter.json)
- [BUG-2026-007](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-007-cross-chapter-hard-cut.json)
- [BUG-2026-008](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-008-page-mode-bottom-clipping.json)
- [BUG-2026-011](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-011-page-boundary-pager-state.json)
- [BUG-2026-012](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-012-boundary-transition-layout-mismatch.json)
- [BUG-2026-018](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-018-page-mode-progress-uses-raw-page-offset.json)
- [BUG-2026-029](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-029-page-mode-body-center-tap-missed.json)
- [BUG-2026-040](/Users/longshengxi/proj/story_app/docs/knowledge/bugs/BUG-2026-040-page-mode-immersive-header-overlaps-body.json)

Relevant page-mode plans/specs:

- [2026-04-17-reader-mode-behavior-refine-design.md](/Users/longshengxi/proj/story_app/docs/superpowers/specs/2026-04-17-reader-mode-behavior-refine-design.md)
- [2026-04-17-reader-mode-behavior-refine.md](/Users/longshengxi/proj/story_app/docs/superpowers/plans/2026-04-17-reader-mode-behavior-refine.md)
- [2026-04-20-reader-topbar-and-page-transition-design.md](/Users/longshengxi/proj/story_app/docs/superpowers/specs/2026-04-20-reader-topbar-and-page-transition-design.md)
- [2026-04-20-reader-topbar-and-page-transition.md](/Users/longshengxi/proj/story_app/docs/superpowers/plans/2026-04-20-reader-topbar-and-page-transition.md)

## Emulator And UI Evidence

Use the Android emulator and Computer Use as available verification tools, not
as optional user-only checks.

Baseline:

- Device: Pixel 8
- Runtime: Android 14 / API 34

Operational notes:

- If the emulator appears unavailable, inspect/wake it before assuming the user
  must intervene.
- Use `adb shell svc power stayon true` or equivalent when long test sessions
  are likely to be interrupted by sleep.
- Use Computer Use for Android Studio/emulator UI actions when automated tests
  cannot prove the interaction.
- Dynamic issues need dynamic evidence when feasible: emulator recording,
  repeated screenshots, logcat, and explicit reproduction steps.
- Static screenshots are only enough for stable final-state visual checks.

Recordings should be considered for:

- animations and transitions
- page jumps, relayout, flashing, or flicker
- auto-hide timing
- gestures, tap zones, long press, and swipe feedback
- TTS follow and highlight motion
- cross-page or cross-chapter turns
- display corruption that appears only after repeated interaction

Available local video tools:

- `/opt/homebrew/bin/ffmpeg`
- `/opt/homebrew/bin/ffprobe`

## Operating Memory Maintenance

Keep this file current when a new standing agreement or reusable capability
appears during the work.

Record here:

- user corrections to agent behavior that should apply beyond the current turn
- tool and plugin capabilities that should be considered by default, such as
  Computer Use for Android Studio/emulator interaction
- video, screenshot, logcat, and emulator setup methods that improve evidence
  quality
- external test data paths and when they should be used
- changes to subagent limits, lifecycle rules, or git cadence

Do not record one-off debugging diaries here. Bug-specific root causes,
failed attempts, and fixes belong in the matching bug JSON and bug index.

## Test Inputs And Real Corpus

Use automated tests first when they can prove the behavior. Use real data when
parser/import/page layout risks depend on realistic TXT structure.

Real novel corpus path provided by the user:

- `/Users/longshengxi/Downloads/小说测试集`

Treat corpus observations as evidence only after preserving the relevant file,
input fragment, command, and observed behavior.

## Subagent Use

Subagents are for independent blind-spot coverage and review. They are not final
authority.

Limits and lifecycle:

1. Small tasks do not use subagents.
2. Normal implementation tasks use at most three subagents: plan review, code
   review, and test-completeness review.
3. Complex tasks use at most four subagents, adding only one focused specialist
   such as page-mode, anchor, Compose display, TTS, or TXT parser/offset.
4. More than four requires an explicit reason and independent high-risk domains.
5. Do not launch duplicate generic reviewers.
6. Give each subagent concrete files, diffs, commands, assumptions, and questions.
7. Require findings with severity, file/line references, and verifiable facts.
8. The main agent must resolve every finding as fixed, factually rejected, or
   recorded residual risk.
9. Close/release finished subagents with the available harness mechanism before
   claiming the task is complete.

## Git Cadence

Prefer small Chinese commits after each solved issue or coherent structural
improvement. Keep unrelated dirty worktree files out of the commit.
