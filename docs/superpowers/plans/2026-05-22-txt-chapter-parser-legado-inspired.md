# TXT Chapter Parser Legado-Inspired Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Replace the single-regex TXT chapter splitter with a clean-room, Legado-inspired chapter detection pipeline that handles common Chinese TXT formats, double titles, table-of-contents false positives, and no-title fallback without losing body text.

**Architecture:** Keep `ChapterParser.parse(content): List<ParsedChapter>` compatible, but move the internals to a small pipeline: line indexing -> built-in rule trial -> rule selection -> full-text candidate scan -> candidate filtering/merging -> chapter assembly. Borrow Legado's algorithmic shape, not its GPL source or `txtTocRule.json` data: rule categories are reimplemented from observed TXT patterns and covered by local tests.

**Tech Stack:** Kotlin/JVM, Android app data layer, existing `TxtNormalizer`, JUnit tests via Gradle.

---

## Context And Constraints

- Current parser files:
  - `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterParser.kt`
  - `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterPatterns.kt`
- Current integration points:
  - `app/src/main/java/com/longerlsx/storyapp/data/book/ImportCoordinator.kt`
  - `app/src/main/java/com/longerlsx/storyapp/data/book/InMemoryBookRepository.kt`
  - `app/src/main/java/com/longerlsx/storyapp/core/model/Chapter.kt`
- Existing tests:
  - `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterParserTest.kt`
  - `app/src/test/java/com/longerlsx/storyapp/data/book/ImportCoordinatorTest.kt`
  - `app/src/test/java/com/longerlsx/storyapp/data/book/ImportedBookCatalogRestoreTest.kt`
- Reference projects:
  - Legado tries enabled TXT TOC rules on the file head, selects a likely rule, scans the full book, and falls back to length-based segmentation.
  - Koodo supports parser selection/custom parser concepts, but its parser is bundled/minified and less directly reusable.
  - HwTxtReader shows a smaller matcher-hook model with paragraph/character offsets.
- Do not copy Legado code or `txtTocRule.json` verbatim. Legado is GPL-3.0; this project should use a clean-room implementation with tests proving behavior.
- During implementation, do not open Legado source or rule JSON for copy/paste work. Use only the generic behavior categories documented in this plan and locally authored fixtures/tests.
- There are existing unrelated dirty changes in reader/page-mode files. Do not revert or fold those into this refactor.

## File Structure

- Modify: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterParser.kt`
  - Keep the public `parse` entry point.
  - Delegate to the new internal pipeline.
  - Preserve compatibility for callers that only need `List<ParsedChapter>`.
- Modify or replace: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterPatterns.kt`
  - Convert from one hard-coded regex into built-in rule definitions or helpers.
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterLine.kt`
  - Represents a normalized text line with raw line text, trimmed text, line index, start offset, and end offset.
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterDetectionRule.kt`
  - Defines rule id, priority, title kind, and matcher.
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterCandidate.kt`
  - Represents one potential chapter heading with title, normalized number, offsets, rule id, confidence signals, and line index.
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterNumber.kt`
  - Parses Arabic and Chinese chapter numbers for sequence scoring and double-title equivalence.
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterParseResult.kt`
  - Holds `chapters`, selected rule metadata, diagnostics, and candidate counts for tests/logging.
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterRuleSelector.kt`
  - Runs built-in rules on the front sample and selects the best primary rule plus allowed auxiliary rules.
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterCandidateScanner.kt`
  - Applies the selected primary and auxiliary rules to the full normalized text.
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterCandidateFilter.kt`
  - Filters false positives and table-of-contents candidates.
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterCandidateMerger.kt`
  - Merges adjacent double-title candidates.
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterAssembler.kt`
  - Converts merged candidates into `ParsedChapter` objects with stable body text and offsets.
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterParserTest.kt`
  - Add parser-level examples and invariants.
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterRuleSelectorTest.kt`
  - Test rule trial and selection.
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterCandidateScannerTest.kt`
  - Test full-text scanning after rule selection.
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterCandidateMergerTest.kt`
  - Test double-title and adjacent-title behavior.
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ImportCoordinatorTest.kt`
  - Add import text assertions for generated chapters.
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ImportedBookCatalogRestoreTest.kt`
  - Add hydrate/restart assertion so restored chapter body does not regain title lines.
- Create fixtures:
  - `app/src/test/resources/fixtures/txt_double_title_spaced_number.txt`
  - `app/src/test/resources/fixtures/txt_front_toc_then_body.txt`
  - `app/src/test/resources/fixtures/txt_volume_then_chapter.txt`
  - `app/src/test/resources/fixtures/txt_no_title_long.txt`

## Rule Scope For Phase 1

Implement a compact built-in ruleset inspired by Legado categories:

- High-confidence numbered Chinese chapter rules:
  - `第1章 标题`
  - `第 1 章`
  - `第　1　章`
  - `第001章`
  - `第一章`
  - suffixes: `章`, `回`, `节`, `话`, `集`
- Special chapter markers:
  - `序章`, `楔子`, `番外`, `番外一`, `后记`, `尾声`
- Numeric title rules:
  - `1. 标题`
  - `001 标题`
  - `一、标题`
- Low-confidence structural markers:
  - `前言`, `正文`
  - Treat these as preface/body separators, not normal chapters unless no better rule exists.
- Volume-like markers:
  - `第一卷`, `第一部`, `第一篇`
  - Treat as section prefixes. Do not create empty standalone chapters when a normal chapter follows immediately. If `第一卷 风起` is followed by `第一章 初见`, chapter output should still be `第一章 初见`; carrying the volume name into the visible chapter title is deferred.

Explicitly defer:

- Full Legado rule parity.
- User-editable custom regex UI.
- JavaScript/custom parser execution.
- Byte-stream scanning and file buffer cache rewrite.
- Very large file window parsing.
- English `Chapter` rules unless they can be added with low risk after Chinese cases pass.

## Semantic Decisions

- `ParsedChapter.title` for a double title uses the first physical title line, because it is the actual boundary marker and tends to be the stable TOC form in the source file.
- `ParsedChapter.content` starts after the last merged title line.
- Phase 1 must define `ParsedChapter.startOffset` and persisted `Chapter.startOffset` as the body start offset, not the heading start offset.
- `ParsedChapter.endOffset` and persisted `Chapter.endOffset` must be the body end offset after boundary trimming.
- For every parsed chapter, `normalized.substring(startOffset, endOffset)` must equal `ParsedChapter.content` exactly. If the assembler trims leading or trailing boundary whitespace, it must adjust `startOffset`/`endOffset` at the same time instead of trimming only the returned string.
- The original heading offset can remain diagnostics-only unless a UI feature needs it later.
- Candidate count and distance are scoring signals, not hard rejection rules. A one-chapter book must still parse.
- All accepted chapters must have monotonic, non-overlapping offsets inside `TxtNormalizer.normalize(content)`.

---

### Task 1: Characterize Current Failures

**Files:**
- Modify: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterParserTest.kt`
- Create: `app/src/test/resources/fixtures/txt_double_title_spaced_number.txt`

- [x] **Step 1: Add a failing test for spaced-number double titles**

Use a fixture shaped like:

```text
文案

第 1 章
第一章
正文第一段。

第 2 章
第二章
正文第二段。
```

Assert:

- Titles are `前言`, `第 1 章`, `第 2 章`.
- Chapter 1 content starts with `正文第一段。`.
- Chapter 1 content does not contain `第一章`.
- Chapter 2 content starts with `正文第二段。`.

- [x] **Step 2: Run the targeted test and verify it fails**

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterParserTest`

Expected: FAIL because current `ChapterPatterns` does not match `第 1 章` and drops only one title line.

- [x] **Step 3: Add current-regression tests for existing behavior**

Keep assertions for:

- Existing `第1章 标题` parsing.
- Preface before first chapter.
- `第一章正文。` is body text, not a heading.

- [x] **Step 4: Run the targeted test**

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterParserTest`

Expected: existing regression tests PASS, new double-title test FAIL.

---

### Task 2: Add Line Indexing And Number Parsing

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterLine.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterNumber.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterNumberTest.kt`

- [x] **Step 1: Write number parsing tests**

Cover:

- `1`, `001`
- `一`, `二`, `十`, `十一`, `二十`, `一百零二`
- `两`

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterNumberTest`

Expected: FAIL because `ChapterNumber` does not exist.

- [x] **Step 2: Implement `ChapterNumber`**

Keep it small and deterministic:

- `value: Int?`
- `raw: String`
- `parse(raw: String): ChapterNumber`

Do not support every historical numeral variant in phase 1.

- [x] **Step 3: Implement `ChapterLine` indexing helper**

Expose an internal helper used by `ChapterParser`:

- `lineIndex`
- `text`
- `trimmed`
- `startOffset`
- `endOffsetExclusive`

Offsets must be based on the normalized string passed into the parser.

- [x] **Step 4: Run tests**

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterNumberTest`

Expected: PASS.

---

### Task 3: Build The Clean-Room Rule Set

**Files:**
- Modify or replace: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterPatterns.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterDetectionRule.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterCandidate.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterDetectionRuleTest.kt`

- [x] **Step 1: Write matcher tests**

Test accepted headings:

- `第1章 标题`
- `第 1 章`
- `第　1　章`
- `第001章`
- `第一章`
- `第十回`
- `番外一`
- `后记`

Test rejected body lines:

- `第一章正文。`
- `第1章 开始了。`
- `这已经是第三回了。`
- A line longer than the configured heading length.

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterDetectionRuleTest`

Expected: FAIL because rule objects do not exist.

- [x] **Step 2: Implement built-in rule definitions**

Use internally owned regexes and helper code. Do not copy Legado regex text.

Each match should emit:

- display title
- optional chapter number
- title kind: chapter, special, numeric, structural, volume
- confidence class
- line and offsets
- rule id

- [x] **Step 3: Preserve `ChapterPatterns.isChapterTitle` temporarily**

If existing tests or callers still use `ChapterPatterns.isChapterTitle`, make it delegate to the new rule set rather than deleting it immediately.

- [x] **Step 4: Run matcher tests**

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterDetectionRuleTest`

Expected: PASS.

---

### Task 4: Implement Legado-Inspired Rule Trial And Selection

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterParseResult.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterRuleSelector.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterRuleSelectorTest.kt`

- [x] **Step 1: Write rule selection tests**

Cover:

- A normal `第N章` book selects the Chinese numbered chapter rule.
- `第 1 章` spaced-number book selects the spaced numbered rule.
- A front table of contents does not win over repeated real body headings.
- A one-chapter book still selects a usable rule.
- A text with only `正文` does not select structural markers as a high-confidence chapter rule.

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterRuleSelectorTest`

Expected: FAIL.

- [x] **Step 2: Implement front-sample trial**

Sample policy:

- Use the first 100 KB or first 2,000 lines, whichever comes first.
- Run all built-in rules independently.
- Score each rule using:
  - candidate count
  - non-zero body distance between candidates
  - sequence consistency when numbers exist
  - penalty for many candidates clustered in the opening TOC-like block
  - penalty for structural-only matches

- [x] **Step 3: Select primary and auxiliary rules**

Primary rule:

- One best high-confidence numbered/numeric rule.

Auxiliary rules:

- Special markers such as `番外`, `后记`, `尾声`.
- Structural markers only if no better rule exists.

Candidate count and distance must not be hard failure gates.

- [x] **Step 4: Run selector tests**

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterRuleSelectorTest`

Expected: PASS.

---

### Task 5: Scan Full Text With Selected Rules

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterCandidateScanner.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterParser.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterCandidateScannerTest.kt`

- [x] **Step 1: Write full-scan tests**

Cover:

- A heading that appears after the first 100 KB / 2,000-line sample is still found during full scan.
- Primary rule candidates and auxiliary `番外`/`后记` candidates are both included.
- Rules that lost selection during front-sample trial are not allowed to add unrelated false positives during full scan.
- A short one-chapter file is still scanned and returned as a valid single chapter candidate.

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterCandidateScannerTest`

Expected: FAIL because `ChapterCandidateScanner` does not exist.

- [x] **Step 2: Implement full-text candidate scanner**

Inputs:

- normalized `List<ChapterLine>`
- selected primary rule
- selected auxiliary rules

Output:

- ordered candidates from the full normalized text, not only from the front sample.

Rules:

- Scan all lines in order.
- Apply the primary rule first.
- Apply auxiliary special/structural rules only when they do not conflict with a primary-rule match on the same line.
- Preserve original line index and offsets for later filtering, merging, and assembly.

- [x] **Step 3: Wire scanner into `parseDetailed`**

Flow must be:

```text
normalize -> lines -> front-sample rule trial -> full-text scan -> filter -> merge -> assemble
```

- [x] **Step 4: Run scanner tests**

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterCandidateScannerTest`

Expected: PASS.

---

### Task 6: Filter TOC False Positives And Merge Double Titles

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterCandidateFilter.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterCandidateMerger.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterCandidateMergerTest.kt`
- Test: `app/src/test/resources/fixtures/txt_front_toc_then_body.txt`
- Test: `app/src/test/resources/fixtures/txt_volume_then_chapter.txt`

- [x] **Step 1: Write double-title merge tests**

Cases:

- `第 1 章` followed by `第一章` merges into one candidate.
- Body starts after the second title line.
- The accepted title is `第 1 章`.
- Two real short chapters are not merged just because they are close.

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterCandidateMergerTest`

Expected: FAIL.

- [x] **Step 2: Write TOC false-positive tests**

Fixture shape:

```text
目录
第1章 初见
第2章 重逢

第1章 初见
正文一。

第2章 重逢
正文二。
```

Assert the parsed chapters are the real body chapters, not empty TOC chapters.

Also add parser-level tests for:

- A single real chapter near the front still parses as one chapter.
- A short body line that resembles a heading but ends with sentence punctuation does not split the chapter.
- `第一卷 风起` followed by `第一章 初见` does not create an empty `第一卷` chapter.

- [x] **Step 3: Implement filter**

Rules:

- Opening clusters of headings with no body between them are TOC candidates.
- If matching numbered headings appear later with body text, prefer the later body headings.
- Do not discard a real one-chapter file just because it appears near the front.

- [x] **Step 4: Implement merger**

Merge only when:

- candidates are adjacent or separated by one blank line;
- both are heading-like candidates;
- numbers are equal or title forms are equivalent;
- no meaningful body line exists between them.

- [x] **Step 5: Run filter/merge tests**

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterCandidateMergerTest`

Expected: PASS.

---

### Task 7: Assemble Chapters With Stable Body Offsets

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterParser.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterAssembler.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterParserTest.kt`

- [x] **Step 1: Write assembly invariants**

Assert:

- `ParsedChapter.content` never includes accepted heading lines.
- `startOffset` is the first character of `ParsedChapter.content` in the normalized source.
- `endOffset` is one past the last character of `ParsedChapter.content` in the normalized source.
- `normalized.substring(startOffset, endOffset) == ParsedChapter.content`.
- `endOffset` is monotonic and inside the normalized content.
- Preface content before the first real heading remains in `前言`.
- No non-heading body paragraph is silently dropped.

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterParserTest`

Expected: FAIL until assembler is wired.

- [x] **Step 2: Implement assembler**

For each merged candidate:

- body starts at the end of the last merged heading line;
- body ends at the next accepted candidate's first heading line;
- compute boundary trimming first, then move `startOffset` and `endOffset` to the trimmed body range;
- trim only boundary whitespace, not internal paragraph spacing;
- word count remains computed from `ParsedChapter.content`.

- [x] **Step 3: Wire `ChapterParser.parseDetailed`**

Add an internal or public-for-test method:

- `parseDetailed(content: String): ChapterParseResult`

Make `parse(content)` return `parseDetailed(content).chapters`.

- [x] **Step 4: Run parser tests**

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterParserTest`

Expected: PASS.

---

### Task 8: Implement No-Title And Long-Text Fallback

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterAssembler.kt`
- Test: `app/src/test/resources/fixtures/txt_no_title_long.txt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterParserTest.kt`

- [x] **Step 1: Write fallback tests**

Cases:

- Short no-title text becomes one `正文` chapter.
- Long no-title text splits by paragraph boundary.
- Long fallback does not drop paragraphs.
- Long fallback does not split inside a paragraph when a paragraph boundary is available.

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterParserTest`

Expected: FAIL for long fallback until implemented.

- [x] **Step 2: Implement fallback policy**

Phase-1 policy:

- Short no-title threshold: keep existing single `正文` behavior.
- Long no-title threshold: split near a target size on paragraph boundary.
- Use generated titles: `正文 1`, `正文 2`, etc.

- [x] **Step 3: Run fallback tests**

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ChapterParserTest`

Expected: PASS.

---

### Task 9: Fix Import/Hydrate Consistency

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/book/ImportCoordinator.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/book/InMemoryBookRepository.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ImportCoordinatorTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ImportedBookCatalogRestoreTest.kt`

- [x] **Step 1: Write import assertions**

Import a text with double titles. Assert:

- Repository chapter text does not contain either heading line.
- Chapter title is stable.
- Word count is based on body content, not heading lines.

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ImportCoordinatorTest`

Expected: FAIL until parser/offset semantics are fixed.

- [x] **Step 2: Write hydrate/restart assertions**

Use `ImportedBookCatalogRestoreTest` to persist and hydrate the same imported book. Assert:

- Restored `getChapterText()` equals the original imported chapter text.
- Restored text does not regain heading lines.

Run: `./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ImportedBookCatalogRestoreTest`

Expected: FAIL before hydrate consistency fix.

- [x] **Step 3: Update integration for mandatory body offsets**

- `ImportCoordinator` can keep mapping `parsed.startOffset` to `Chapter.startOffset`.
- `InMemoryBookRepository.hydrateFromStorage()` continues slicing `startOffset..endOffset`, but now slices body text.
- Do not persist heading offsets in phase 1; keep heading offsets diagnostics-only.
- Assert that hydrate slicing exactly reproduces `ParsedChapter.content`, not merely a trimmed equivalent.

- [x] **Step 4: Run integration tests**

Run:

```bash
./gradlew testDebugUnitTest --tests com.longerlsx.storyapp.data.book.ImportCoordinatorTest --tests com.longerlsx.storyapp.data.book.ImportedBookCatalogRestoreTest
```

Expected: PASS.

---

### Task 10: Add Diagnostics And Documentation

**Files:**
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterParseResult.kt`
- Modify: `docs/knowledge/bugs/INDEX.md`
- Create: `docs/knowledge/bugs/BUG-2026-013-txt-chapter-parser-legado-inspired.json`

- [x] **Step 1: Add diagnostics fields**

Include:

- selected primary rule id
- auxiliary rule ids
- candidate counts before/after filtering
- TOC candidates dropped
- double titles merged
- fallback mode used

- [x] **Step 2: Keep diagnostics out of UI**

Do not add reader UI or settings UI in phase 1. Diagnostics are for tests and future logging.

- [x] **Step 3: Add bug knowledge entry**

Document:

- sample shape: `第 1 章` + `第一章`
- root cause: single narrow regex and one-heading-line drop
- broader fix: Legado-inspired clean-room parser
- verification commands

---

### Task 11: Full Verification And Review

**Files:**
- All files touched above.

- [x] **Step 1: Run data-book tests**

Run:

```bash
./gradlew testDebugUnitTest --tests 'com.longerlsx.storyapp.data.book.*'
```

Expected: PASS.

- [x] **Step 2: Run full unit test suite**

Run:

```bash
./gradlew testDebugUnitTest
```

Expected: PASS.

- [x] **Step 3: Compile debug Kotlin if needed**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: PASS.

- [x] **Step 4: Launch two review subagents after implementation**

Open two subagents:

- Code review agent: inspect architecture, false positives, offset semantics, GPL clean-room boundary.
- Test completeness agent: inspect coverage for double titles, TOC false positives, fallback, import/hydrate, and regressions.

- [x] **Step 5: Fix review findings**

Address valid findings and rerun affected tests.

- [x] **Step 6: Close review subagents**

After each review agent returns a final result, explicitly close/release it before reporting completion. This follows the required subagent lifecycle rule in `docs/knowledge/README.md`.

- [x] **Step 7: Prepare final summary**

Report:

- rules implemented
- source examples covered
- tests run
- known deferred work

Do not commit implementation together with unrelated existing dirty reader/page-mode changes.
