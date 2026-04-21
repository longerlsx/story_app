# Reader Topbar And Page Transition Design

## Overview

This slice refines two parts of the reader that still feel off in daily use:

- the top area does not match the intended reading chrome hierarchy
- page mode still feels cut apart when crossing chapter boundaries

The current reader already has:

- immersive center-tap chrome
- a persistent top-left reading identity
- scroll mode with continuous chapter flow
- page mode with chapter-local pagination

The remaining gap is mostly interaction quality.

This design separates the reader into two clearly different top states and makes cross-chapter page turning follow the same interaction path as in-chapter page turning.

## In Scope

- Replace the current floating persistent top-left block with a lighter immersive reading header
- Introduce a separate full top bar when the reader chrome is visible
- Keep the visible-chrome top bar aligned with the bottom action area
- Reserve a top-right `...` affordance as a visual placeholder only
- Make page-mode chapter boundary transitions feel like the same horizontal page-turn flow used inside a chapter
- Keep the existing bottom interaction model unless explicitly changed here

## Out Of Scope

- Adding real behavior to the top-right `...` action
- Rebuilding bookshelf flows
- New note, bookmark, or annotation features
- Paper curl animation or heavy visual effects
- Changing the existing settings information architecture

## Design Goals

- Make immersive reading feel calm and unobtrusive
- Make visible chrome feel structured instead of layered on top of another header
- Remove the visual "hard cut" when paging across chapter boundaries
- Preserve stable anchor persistence and restoration
- Keep both reading modes under one coherent chrome model

## Top Structure

### Reading-Only State

When the reader is in immersive mode, the top area should be minimal.

Content:

- back arrow only
- current chapter number and title

Rules:

- no `返回` text
- no floating card or capsule block
- no obvious background fill
- no separate shadow or elevation treatment
- text and icon should sit directly on the reading background, using theme-aware color with slightly reduced emphasis compared to body text

The intended feel is similar to a light overlay label, not a toolbar.

### Chrome-Visible State

When the user taps the center and the reader chrome becomes visible, the immersive top-left header should disappear and be replaced by a full-width top bar.

Structure:

- left: back button
- center: book title and current chapter title
- right: `...` placeholder

Rules:

- the top bar appears together with the bottom action layer
- the immersive header does not remain visible underneath it
- the top bar should feel like the top edge of the chrome system, not like a second floating widget
- the top-right `...` should render and accept click feedback, but should not open anything in this slice

This top bar is the one that matches the reference screenshot behavior.

## Top State Switching

There are only two top states:

- immersive reading header
- full chrome top bar

Switching rules:

- `reading-only -> chrome-visible`: fade out the immersive header and fade in the full top bar
- `chrome-visible -> reading-only`: fade out the full top bar and restore the immersive header

Non-goals:

- no morph animation from the small header into the large bar
- no shared moving capsule or transforming container

The transition should feel clean and restrained rather than decorative.

## Mode Sharing

Both reading modes use the same top-state model.

### Scroll Mode

- immersive state uses the light top-left reading header
- chrome-visible state uses the full top bar plus the existing bottom primary action row
- there is no chapter navigation row in scroll mode

### Page Mode

- immersive state uses the same light top-left reading header
- chrome-visible state uses the full top bar plus the bottom action area
- page mode keeps its chapter navigation row above the bottom primary action row

The difference between modes is in the reading surface and bottom control behavior, not in the top chrome hierarchy.

## Page Transition Behavior

### Problem

Inside a chapter, page mode already uses one horizontal paging surface.  
At chapter boundaries, however, the current behavior still feels like:

- finish one chapter
- replace content source
- restart the pager

That creates a visible discontinuity even if the resulting page content is correct.

### Target Behavior

Cross-chapter paging should feel like the same motion path as normal in-chapter paging.

Rules:

- if the user pages forward from the last page of a chapter, the next surface should be the first page of the next chapter
- if the user pages backward from the first page of a chapter, the next surface should be the last page of the previous chapter
- both of those transitions should use the same horizontal page-turn interaction style as chapter-internal paging

The user should feel that they kept paging left or right, not that the reader rebuilt itself in place.

### Implementation Direction

This slice should not aim for a heavy animation system.  
Instead, it should unify the transition path:

- chapter-internal paging and chapter-boundary paging both enter the same horizontal turn experience
- chapter-boundary transitions preload or prepare the destination page before the visible turn completes
- the reader should avoid a hard chapter swap between frames

This is a hand-feel improvement, not a visual-effects project.

## Boundary Semantics

The semantic rules for where chapter boundaries land remain:

- forward boundary -> next chapter first page
- backward boundary -> previous chapter last page

What changes in this slice is not the destination, but the continuity of the motion.

## Data And Restoration Implications

The persisted model remains anchor-based:

- `bookId`
- `chapterIndex`
- `charOffset`
- `readingMode`

Additional expectations:

- when page mode crosses into a neighboring chapter, the new page anchor must be saved from the destination chapter
- page restore must not reuse stale page slices from the previously visible chapter
- the active reading header must follow the actual current chapter after the cross-chapter transition completes

## Error Handling

- if a neighboring chapter has no valid paginated pages, fall back to opening that chapter at offset `0`
- if destination page preparation fails, the reader may still complete the chapter switch, but it should not freeze or leave the pager in an invalid state
- if the chrome is opened during a boundary transition, the top bar should still reflect the resolved destination chapter

## Testing Strategy

### Unit Tests

- active chapter resolution prefers a real chapter over a visible preface block
- page-boundary resolution still maps backward movement to previous chapter last-page anchor
- page restore only proceeds when the loaded content belongs to the target chapter

### Instrumented Tests

- immersive state shows only a light `back arrow + chapter` header, not the full top bar
- opening reader chrome hides the immersive header and shows the full top bar
- scroll mode still reveals only the primary bottom action row
- directory jump still returns to reading-only state
- page mode backward paging across a chapter boundary lands near the previous chapter end
- page mode forward paging across a chapter boundary lands at the next chapter start

## Acceptance Criteria

This slice is successful when:

- the persistent top-left reading header no longer looks like a floating card
- the reading-only top area shows only `back arrow + chapter number/title`
- opening chrome replaces that small header with a full-width top bar
- the top-right `...` is visible as a placeholder in chrome-visible state
- cross-chapter page turns no longer feel like an abrupt hard chapter cut
- anchor persistence and restoration still behave correctly after cross-chapter page transitions
