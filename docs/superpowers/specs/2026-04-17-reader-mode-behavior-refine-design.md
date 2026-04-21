# Reader Mode Behavior Refine Design

## Overview

This document refines the reader behavior after the first interaction refresh. The current reader already supports:

- immersive center-tap chrome
- directory and in-place settings
- scroll mode
- page-turn mode
- anchor persistence

The remaining issue is that the two reading modes still behave too similarly in places where users expect them to differ.

This slice separates the mode logic more clearly:

- page mode behaves like a discrete page reader
- scroll mode behaves like a continuous chapter stream
- the top-left reading identity becomes persistent instead of hiding with the chrome

## In Scope

- Make page mode jump to the previous chapter's last page when paging backward across a chapter boundary
- Convert scroll mode into a true continuous multi-chapter scroll flow
- Keep a compact persistent top-left reader header
- Show current chapter number/title in that persistent header
- Keep the bottom action area mode-aware
- Update anchor persistence and restoration for continuous scroll mode

## Out of Scope

- Rebuilding bookshelf behavior
- New notes, highlights, or bookmarks
- Paper-like page curl animation
- Deep visual skinning beyond what is needed for the new behavior
- Per-book behavior overrides

## Design Goals

- Make page mode feel like page turning, not chapter jumping
- Make scroll mode feel uninterrupted across chapters
- Reduce chrome dependence for basic orientation and back navigation
- Preserve stable restoration after mode switches, chapter jumps, app restarts, and background kills

## Mode-Specific Behavior

### Page Mode

Page mode remains chapter-based.

Rules:

- Each chapter is paginated independently
- Tapping right advances to the next page
- Tapping left goes to the previous page
- If the user is on the last page of a chapter and moves forward, the reader opens the next chapter at its first page
- If the user is on the first page of a chapter and moves backward, the reader opens the previous chapter at its last page

This means cross-chapter movement is asymmetric by design:

- forward boundary -> next chapter, first page
- backward boundary -> previous chapter, last page

That behavior matches common page-reader expectations better than reopening the previous chapter at its beginning.

### Scroll Mode

Scroll mode should no longer behave as a single-chapter scroll with explicit chapter stepping.

Rules:

- The reading surface becomes one continuous chapter stream for the whole book
- Chapters are rendered as sequential blocks inside one lazy scroll container
- The user does not need `上一章 / 下一章` while in scroll mode
- Reaching the end of one chapter naturally continues into the next chapter
- Reaching the top of the current visible chapter naturally reveals the previous chapter

## Persistent Top-Left Header

The current top bar is too transient for basic orientation and back navigation.

This slice replaces that behavior with a persistent compact header in the top-left area.

### Content

- Back button
- Current chapter number and chapter title

### Behavior

- Always visible in both scroll mode and page mode
- Visually compact enough not to dominate the reading surface
- Independent from center-tap chrome visibility
- Back immediately returns to the bookshelf after saving current progress

The existing full-width transient top bar should be removed in favor of this compact persistent variant.

## Bottom Controls by Mode

### Shared Behavior

The primary bottom action bar still appears via center tap and still contains:

- `目录`
- `日/夜切换`
- `设置`

Settings and directory expansion behavior remain unchanged unless called out below.

### Page Mode Bottom Controls

Page mode keeps the chapter-navigation row above the primary action bar.

Content:

- `上一章`
- progress summary
- `下一章`

Behavior:

- Explicit chapter stepping is still useful in page mode
- Chapter stepping keeps the existing chrome-state rules

### Scroll Mode Bottom Controls

Scroll mode removes the explicit chapter-navigation row.

Behavior:

- No `上一章 / 下一章` row
- Only the primary action bar is shown when chrome is visible
- Chapter transitions happen through the scroll flow itself, not explicit stepping

## Current Chapter Resolution

The reader must resolve a "current chapter" differently by mode.

### Page Mode

The current chapter is simply the chapter currently open in the pager.

### Scroll Mode

The current chapter should be derived from the visible chapter block nearest the top of the viewport.

Practical rule:

- Track visible chapter items in the scroll container
- Use the first chapter whose content is currently intersecting the top reading area as the active chapter
- If multiple chapters are partially visible, prefer the earliest visible chapter that owns the top-most visible content

This is more stable than inferring chapter changes from arbitrary absolute pixel thresholds.

## Anchor Model and Restoration

The existing persisted anchor model can remain chapter-based, but scroll mode must save it differently.

### Shared Persisted Anchor

Persist:

- `bookId`
- `chapterIndex`
- `charOffset`
- `readingMode`

### Page Mode Persistence

- Save the current chapter plus the current page's start offset
- Cross-chapter backward paging into the previous chapter's last page must persist that previous chapter anchor

### Scroll Mode Persistence

- Save the chapter that currently owns the top-most visible reading content
- Save the approximate chapter-local char offset corresponding to that top-most visible position
- On restore, reopen the continuous chapter stream and scroll to the saved chapter block and approximate intra-chapter offset

Scroll mode restoration does not need pixel-perfect restoration in this slice, but it should return close enough that the top visible paragraph is recognizably the same reading location.

## Data-Flow Implications

### Page Mode

- Page pagination remains chapter-local
- Boundary navigation logic needs one additional path: open previous chapter at last page

### Scroll Mode

- The screen must load a chapter stream instead of only the selected chapter text
- Directory selection still scrolls/jumps to the selected chapter within the continuous stream
- The active chapter label and progress saving should use the stream's visible-item state

## Error Handling

- If the previous chapter has no paginated content, backward boundary falls back to the previous chapter start as a safety case
- If scroll restoration cannot resolve an exact intra-chapter position, restore to the chapter start rather than the wrong chapter
- If the chapter stream loads partially, the reader should still open and then settle once content is ready

## Testing Strategy

### Unit Tests

- previous-chapter page-boundary logic resolves to last page
- scroll-mode active chapter resolution from visible items
- continuous-scroll anchor save/restore mapping

### Instrumented Tests

- page mode backward boundary opens previous chapter near its end, not its start
- scroll mode can move seamlessly from one chapter into the next without explicit chapter buttons
- persistent top-left header is visible in both modes
- back button from persistent header saves progress and returns to bookshelf
- directory jump in scroll mode lands on the chosen chapter inside the continuous stream

## Acceptance Criteria

This slice is successful when:

- page mode no longer reopens the previous chapter at its first page when paging backward across a boundary
- scroll mode reads like one continuous multi-chapter surface
- the reader always shows a compact top-left `back + current chapter` header
- scroll mode no longer depends on explicit previous/next chapter buttons
- reading progress still restores to the correct chapter and close to the previous reading location
