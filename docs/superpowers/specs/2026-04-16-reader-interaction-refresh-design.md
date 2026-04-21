# Reader Interaction Refresh Design

## Overview

This document refines the phase-one reader interaction model so the app feels closer to a mature Android novel reader. The functional baseline already exists: import, bookshelf, directory, scroll mode, page-turn mode, settings persistence, and anchor restoration. This refresh focuses on interaction hierarchy, control placement, and mode-specific theme memory.

The goal is not to clone any single commercial reader pixel-for-pixel. The goal is to match the same interaction logic:

- reading surface is immersive by default
- a center tap reveals both top and bottom controls
- the bottom area is layered
- the middle primary button directly toggles day/night mode
- settings expand upward in place instead of replacing the reading surface
- directory jump returns directly to the reading surface

## In Scope

- Refresh the scroll-reader control hierarchy
- Reuse the same hierarchy for page-turn mode where practical
- Replace the current generic control cluster with a two-layer bottom control area
- Add a direct day/night toggle in the center of the primary bottom bar
- Expand settings upward from the bottom area instead of opening a separate bottom sheet
- Separate day-theme memory from night-theme memory
- Move reading-mode switching into expanded settings instead of the primary bar
- Replace page padding control with paragraph spacing control

## Out of Scope

- Rebuilding the bookshelf
- Adding notes, highlights, or bookmarks
- Deep visual skinning or texture asset work
- Full commercial-reader feature parity
- Complex animation-style selectors for page turning
- Per-book settings overrides

## Interaction Goals

### Primary Goals

- Make the reader feel immersive first and tool-driven second
- Put the most common action, day/night switching, one tap away
- Keep high-frequency actions reachable without opening a separate page
- Let settings changes remain visually connected to the live reading surface
- Preserve reading position and reader state while the user adjusts controls

### Non-Goals

- Maximizing the number of visible controls at once
- Exposing every future setting in this slice
- Mimicking every icon or spacing detail from the reference images

## Reader Layering Model

The reader should have four visual states:

1. Reading-only state
2. Chrome-visible state
3. Settings-expanded state
4. Directory-open state

Only one expanded panel should exist at a time.

### 1. Reading-Only State

- Only the reading content is visible
- No persistent top bar
- No persistent bottom bar
- Tap center to reveal the reader chrome

### 2. Chrome-Visible State

When the user taps the center area:

- top bar appears
- bottom chapter-navigation row appears
- bottom primary action bar appears

This is the default surfaced control state.

### 3. Settings-Expanded State

When the user taps `设置` in the primary bottom bar:

- the chapter-navigation row is replaced by an expanded settings panel
- the primary action bar remains as the base layer beneath it
- the reading content remains visible behind the expanded settings area
- settings changes apply live without leaving the reader

### 4. Directory-Open State

When the user taps `目录`:

- the settings panel must close first if open
- the directory panel opens as the active expanded layer
- selecting a chapter jumps immediately
- after chapter jump, the reader returns directly to reading-only state

## Top Bar

The top bar appears and hides together with the bottom control area.

### Content

- Left: back
- Center: book title and/or current chapter context
- Right: lightweight overflow placeholder is acceptable, but not required in this slice

### Behavior

- Shown on center tap
- Hidden on center tap again
- Hidden after idle timeout when only the basic chrome is visible
- Not auto-hidden while directory or expanded settings is open

## Bottom Control Area

The bottom control area is split into two layers.

### Bottom Layer A: Chapter Navigation Row

Visible in chrome-visible state, hidden when settings is expanded.

Content:

- `上一章`
- progress summary
- `下一章`

Behavior:

- Chapter jump keeps the reader chrome state as-is
- It does not force the user back to reading-only state
- This row is replaced by settings content when settings is expanded

### Bottom Layer B: Primary Action Bar

Always present whenever reader chrome is visible.

Content:

- Left: `目录`
- Center: `日/夜切换`
- Right: `设置`

Behavior:

- `目录` opens the directory panel
- `日/夜切换` immediately switches appearance mode without resetting theme or other reading behavior
- `设置` expands the settings layer upward in place

## Settings Expansion Design

The settings area should feel like it grows from the bottom action bar, not like a separate modal page.

### Requirements

- Expand upward above the primary bottom bar
- Replace the chapter-navigation row while expanded
- Keep the reading surface visible behind it
- Reflect typography and theme changes live
- Collapse when:
  - tapping `设置` again
  - tapping empty reading area
  - switching to directory

### First-Slice Contents

The first expanded version should include:

- Brightness slider
- Font size control
- Line spacing control
- Paragraph spacing control
- Reading mode selector: `滚动 / 翻页`
- Theme selector for the active appearance mode
- Optional lightweight placeholders for:
  - eye-care mode
  - font family
  - more settings

### Explicit Exclusion in This Slice

- Background texture/image import
- Full advanced settings page
- Auto-read feature
- Complex page-turn animation presets

## Day/Night Mode State Model

The current single-theme model is insufficient for the desired interaction. The reader needs explicit appearance mode plus separate remembered theme presets.

### Required Persisted State

- `appearanceMode`
  - `DAY`
  - `NIGHT`
- `dayThemePreset`
- `nightThemePreset`

### Theme Resolution Rule

- If `appearanceMode == DAY`, the active theme is `dayThemePreset`
- If `appearanceMode == NIGHT`, the active theme is `nightThemePreset`

### Day/Night Toggle Rule

The center primary button only changes `appearanceMode`.

It must not reset:

- theme choices
- font size
- line spacing
- paragraph spacing
- reading mode
- reading anchor

### Theme Picker Rule

- Changing theme while in day mode only updates `dayThemePreset`
- Changing theme while in night mode only updates `nightThemePreset`
- Day and night theme memory remain independent

## Reading Mode Placement

The primary action bar should not contain scroll/page toggle.

### Rule

- `滚动 / 翻页` belongs inside expanded settings
- the same bottom hierarchy should conceptually apply in both modes
- this slice prioritizes scroll-mode polish, while page mode reuses the same interaction structure as much as practical

## Tap and Dismiss Rules

### Center Tap

- If reader is in reading-only state, reveal top bar + bottom controls
- If reader is in chrome-visible state, hide them
- If expanded settings is open, center tap collapses expanded settings and chrome
- If directory is open, center tap should defer to directory dismissal behavior

### Auto Hide

Auto-hide applies only when:

- chrome is visible
- no expanded settings is open
- no directory is open
- the user has been idle for a short period

### Mutual Exclusivity

- Opening directory closes expanded settings first
- Opening settings closes directory first
- Only one expanded surface may be active at a time

## Chapter-Jump Rules

Different chapter jump sources should behave differently.

### From Chapter Navigation Row

- `上一章 / 下一章` keeps the current reader chrome state
- If controls were visible, they remain visible after the jump
- If settings was open, the settings panel stays open only if the jump came from the navigation row before settings expansion

### From Directory

- Selecting a chapter from directory jumps immediately
- After the jump, the reader returns directly to reading-only state

## Progress Presentation

The chapter-navigation row should show lightweight progress context.

This slice does not need a fully draggable progress bar, but it should show at least one of:

- current chapter position
- rough chapter count progress
- current reading percentage

The presentation should be compact and centered between `上一章` and `下一章`.

## Data and Persistence Implications

This refresh requires changes to reader settings persistence and active theme resolution.

### Reader Settings

Replace the current single-theme-oriented settings model with fields supporting:

- `readingMode`
- `appearanceMode`
- `dayThemePreset`
- `nightThemePreset`
- `fontSize`
- `lineSpacing`
- `paragraphSpacing`
- `brightness`

### Restoration

On app restart:

- restore last reading anchor
- restore reading mode
- restore appearance mode
- restore the remembered theme for that appearance mode
- restore typography controls

## Testing Strategy

### Unit Tests

- day/night theme memory persistence
- settings store persistence for appearance mode and dual theme presets
- tap-zone classification if adjusted
- paragraph spacing state update logic

### Instrumented Tests

- center tap reveals and hides reader chrome
- middle bottom button toggles day/night mode directly
- directory jump returns to reading-only state
- chapter navigation row keeps chrome visible after previous/next jump
- settings expansion replaces chapter-navigation row
- switching day/night mode preserves remembered day/night themes
- expanded settings changes update the live reader content

## Acceptance Criteria

This refresh is successful when:

- the reader feels immersive by default
- the bottom area matches the two-layer structure
- the center primary action directly toggles day/night mode
- settings expand upward in place instead of opening a separate modal page
- day/night themes are remembered independently
- chapter navigation row and directory behavior follow the agreed state rules
- the user can adjust reading appearance while still seeing the live page behind the controls
