# Android TXT Novel Reader Design

## Overview

This project will build an Android novel reader centered on local TXT reading, with a strong reading experience, stable import flow, and room to extend later. The first phase will support:

- Local TXT import
- External-app open/import on Android
- Automatic chapter detection and table of contents
- Both vertical scrolling and horizontal page-turn reading modes
- Reliable reading-position restoration after backgrounding, process death, or restart
- An online-source entry point and extension seam, without real online-source integration in phase one

The project will be delivered incrementally, but each milestone should remain runnable and testable.

## Product Scope

### In Scope for Phase One

- Android app for reading local TXT novels
- TXT import from system file selection and Android external-app open intents
- Copying imported files into app-controlled private storage
- Metadata extraction from filename and text header
- Chapter recognition for common Chinese novel headings
- Bookshelf with imported books and recent reading state
- Reader with vertical scrolling mode
- Reader with horizontal page-turn mode
- Table of contents and chapter jumping
- Reader settings for high-frequency reading preferences
- Persistent reading anchors across app restarts
- Placeholder online-source entry and architecture seam

### Explicitly Out of Scope for Phase One

- Real online-source crawling or integration
- Account system
- Cloud sync
- Payment or membership
- Comments, community, or recommendation systems
- EPUB, PDF, or other file formats
- Manual chapter rebuilding UI

## Goals and Non-Goals

### Goals

- Make local TXT import feel first-class
- Ensure imported books remain readable even if the original shared URI becomes invalid
- Keep reading smooth across both reading modes
- Restore the user to the correct reading anchor after app restart
- Keep architecture simple enough to ship, but clean enough to extend

### Non-Goals

- Building a full online reading platform in phase one
- Optimizing for every possible text edge case before the core reader is solid
- Designing a backend before local reading is proven

## Architecture

The app should use a balanced architecture: clear boundaries for import, storage, parsing, bookshelf, and reader flows, without over-fragmenting the codebase at the start.

### Proposed Technology Choices

- Kotlin
- Jetpack Compose
- Room
- Kotlin Coroutines and Flow
- Navigation Compose

### Module Boundaries

- `app`
  - Application entry point
  - Navigation setup
  - Dependency wiring
  - External intent receiving and routing
- `core:model`
  - Core entities and value objects
- `core:common`
  - Common utilities, result types, error types, dispatchers, helpers
- `data:book`
  - Local persistence
  - File storage
  - TXT import pipeline
  - Metadata extraction
  - Chapter parsing
  - Repository implementations
- `feature:import`
  - Import entry flow
  - External-app open flow
  - Import progress and failure handling
- `feature:bookshelf`
  - Bookshelf list
  - Recent reading
  - Book open/remove actions
- `feature:reader`
  - Reader screen
  - Table of contents
  - Reading progress
  - Reader settings
  - Reading mode switching
- `feature:source`
  - Online-source entry page and interfaces only

This module list is the target boundary. If the first implementation starts as a single app module with package-level separation, the code should still follow these boundaries so extraction remains straightforward.

## Core Domain Model

### Book

Represents an imported novel.

Fields should include:

- `id`
- `title`
- `author`
- `sourceType` such as `local_import` or `external_intent`
- `importFileName`
- `storedPath`
- `charset`
- `fileHash`
- `wordCount`
- `chapterCount`
- `importedAt`
- `lastReadAt`
- `status`

### Chapter

Represents an indexed chapter range rather than a duplicated body.

Fields should include:

- `id`
- `bookId`
- `chapterIndex`
- `title`
- `startOffset`
- `endOffset`
- `wordCount`

### ReadingProgress

Represents a mode-independent reading anchor and restoration state.

Fields should include:

- `bookId`
- `chapterIndex`
- `charOffset`
- `updatedAt`
- `scrollSnapshot`
- `pageSnapshot`

The anchor must be the real source of truth. Scroll and page snapshots are optimization hints only.

### ReaderSettings

Represents high-frequency reader preferences.

Fields should include:

- `readingMode`
- `fontSize`
- `lineSpacing`
- `pagePadding`
- `theme`
- `brightnessMode`

Phase one should treat these as global settings. Per-book overrides can be layered later if needed.

### SourceEntry

Represents a placeholder online-source integration point.

Fields can include:

- `id`
- `name`
- `enabled`
- `type`
- `configBlob`

Phase one does not need live source behavior, only schema and interface seams where helpful.

## Import and Parsing Flow

The import path is a first-class feature and should be reliable whether the user imports from inside the app or from another Android app such as WeChat.

### Import Entry Points

- System file picker
- Android external-app open intent

### Import Pipeline

1. Receive the file URI and basic metadata such as filename, MIME type, and size.
2. Validate that the file is supported by the current build.
3. Copy the file immediately into app-controlled private storage.
4. Detect encoding, prioritizing common Chinese text encodings.
5. Normalize content for parsing:
   - strip BOM
   - normalize line endings
   - clean obvious whitespace noise
6. Extract metadata from filename and early text header.
7. Parse chapters using heading recognition rules.
8. Persist the book record, chapter index, and initial progress.
9. Add the book to the bookshelf.
10. Open the book directly into the reader.

### Why Copy to Private Storage

External URIs may become unreadable later due to permission revocation or source-app behavior. The app must not depend on the shared URI remaining valid after import starts.

### Encoding Support

Phase one should explicitly target:

- UTF-8
- UTF-8 with BOM
- GBK / GB2312 compatible inputs where feasible
- UTF-16 common variants where feasible

If decoding fails, the app should fail clearly rather than silently importing garbled text.

### Metadata Extraction

Metadata should be inferred from:

- Filename
- Leading text header
- Common patterns such as title and author lines

The provided sample novel shows realistic TXT head matter:

- BOM at file start
- Ad or recommendation lines
- Title line
- Author line
- Introductory blurb before chapter one

The parser must handle this shape without treating all leading lines as body content.

### Chapter Detection

Phase one should automatically detect common chapter titles including:

- `第1章`
- `第一章`
- `第001章`
- `序章`
- `楔子`
- `尾声`

Front matter before the first detected chapter should be preserved as preface content or an equivalent introductory section, not discarded.

### Duplicate Import Handling

The import flow should compute a stable file hash and prevent accidental duplicate import. If the same book already exists, the app should offer a fast path to open the existing entry.

### Interrupted Import Recovery

If the app exits during copy or parsing, startup logic should clean up incomplete state so the bookshelf does not contain broken half-imported entries.

## Reading Data Flow

The reader must not read directly from arbitrary external files. It should consume structured content through repositories backed by the imported local file and chapter offsets.

### Reading Content Source

- The imported original TXT file remains the canonical content source.
- The chapter table stores offsets into the file or normalized content view.
- Reader rendering requests chapter content through repository APIs.

### Shared Content Model Across Modes

Vertical scrolling and horizontal page-turn must use the same:

- chapter list
- text source
- reading anchor
- settings baseline

Only presentation and navigation mechanics should differ. This avoids maintaining two inconsistent progress systems.

### Anchor-Based Progress

True progress must be represented by:

- `bookId`
- `chapterIndex`
- `charOffset`

Page number is not stable across devices or settings changes and must not be the primary persisted progress key.

## Reader Interaction Design

### Reader Layout

The reader should default to an immersive reading surface with no permanently visible toolbars.

### Toolbar Behavior

- Tap the center area to reveal controls.
- Controls auto-hide after a short idle period.
- Tap empty content area to dismiss controls.

### Top Controls

- Back
- Book title
- Current chapter title

### Bottom Controls

- Table of contents
- Progress shortcut
- Reading mode switch
- Typography and theme settings

### Reading Modes

Phase one must support:

- vertical scrolling
- horizontal page-turn

Implementation order may prioritize the more stable vertical mode first, but the architecture should be designed to support both from the start.

### Table of Contents

The contents view should provide:

- current chapter highlight
- fast chapter jump
- accurate alignment with parsed chapter indexes

### Settings

Phase one should expose only high-frequency settings:

- reading mode
- font size
- line spacing
- page padding
- theme

Settings should be simple and dependable rather than exhaustive.

### Online-Source Entry Placement

The placeholder online-source entry should not intrude into the reader controls. It belongs at the bookshelf or a separate entry surface, not the main reading interaction loop.

## Reading Anchor Persistence

Reliable restoration after backgrounding or restart is a core phase-one requirement.

### Requirement

The app should restore the user to the same content anchor after:

- normal background and foreground transitions
- process death
- manual app restart
- app crash followed by restart

### Persistence Strategy

Persist a stable anchor using:

- `bookId`
- `chapterIndex`
- `charOffset`

Persist with throttling and on important lifecycle moments such as:

- reading settles after scrolling
- page turn completes
- chapter changes
- app backgrounds
- user switches books

### Restoration Strategy

On restore:

1. Load the book and last persisted anchor.
2. Load chapter content near the anchor.
3. Reconstruct the current mode-specific presentation.
4. Position the user near the exact anchor.

### Mode-Independent Recovery

Switching between scrolling and page-turn modes must preserve the shared anchor. Restarting the app in either mode should still return the user to the correct content location.

### Fallback Recovery

If chapter parsing changes or offsets drift slightly after reparsing, recovery should attempt a nearby text-based correction rather than dropping the user back to the beginning of the book.

## Error Handling

### Unsupported File Types

If the app receives a non-TXT file, it should clearly state that the current version supports TXT only.

### Decode Failure

If encoding detection fails or text is unreadable, the import should fail explicitly and avoid adding a broken book to the shelf.

### Duplicate Import

If the file hash already exists, show that the book is already imported and offer to open it.

### Invalid Shared URI

If the URI becomes unreadable before copying completes, show a clear import failure and leave no broken shelf entry.

### Chapter Recognition Limitations

If chapter recognition is imperfect, the book must still remain fully readable in a continuous flow. Chapter parsing failure must degrade gracefully rather than blocking reading.

### Progress Restore Failure

If exact recovery is impossible, the app should restore to the nearest viable chapter location instead of resetting to the start.

## Testing Strategy

The first phase should treat import stability and anchor restoration as regression-critical.

### Unit Tests

- encoding detection
- metadata extraction
- chapter recognition
- duplicate detection
- progress persistence and restoration logic

### Integration Tests

- external intent import to bookshelf to reader flow
- interrupted import cleanup
- app restart restoring most recent book and anchor
- mode switch preserving anchor

### UI Tests

- bookshelf open flow
- table of contents jump
- settings changes taking effect
- reader controls reveal and hide

### Sample-Based Validation

Use the provided sample novel as the first realistic test artifact:

- `/Users/longshengxi/Downloads/《小猫咪在星际监狱也会手慢无？》作者：桃花朋.txt`

This sample is useful because it includes:

- front matter before chapter one
- title and author lines
- common chapter heading format
- realistic Chinese TXT formatting

Additional edge-case TXT samples should be added later for encoding and chapter-rule stress testing.

### Regression Priorities

Every meaningful iteration should re-check:

- import succeeds
- chapter indexing is usable
- reading mode switch does not lose position
- app restart restores position reliably

## Milestone Direction

### Phase 0

- Repository bootstrap
- Android app skeleton
- Dependency setup
- Baseline navigation and module or package boundaries

### Phase 1

- TXT import flow
- External intent import
- Bookshelf
- Chapter parsing
- Reader with scrolling and page-turn modes
- Anchor persistence and restart restoration

### Future Phases

- Online-source implementation
- More formats
- Sync and account capability
- Advanced reader features

## Open Decisions Deferred

These are intentionally deferred until after the project skeleton and first reader slice exist:

- exact page-turn rendering strategy
- whether per-book reader settings are needed
- whether source placeholders require persisted configuration in phase one
- how aggressive metadata cleanup should be for noisy TXT headers

## Recommendation

Proceed with a balanced implementation plan:

- prioritize stable TXT import and reading
- design the reader around a shared anchor model from day one
- keep online-source support behind a clean seam only
- ship a runnable local-reader milestone before expanding scope
