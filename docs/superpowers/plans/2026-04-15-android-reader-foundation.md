# Android Reader Foundation Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first runnable Android version of the TXT novel reader with a stable Compose app skeleton, bookshelf shell, TXT parsing foundation, external-file import path, shared reading-anchor model, vertical reading mode, and a follow-up slot for horizontal page-turn mode.

**Architecture:** Start with a single `app` module but keep package boundaries aligned with the approved design so later extraction remains straightforward. To reduce setup risk on this machine, bootstrap the project with dependency choices that are likely to hit existing local Gradle caches first; introduce heavier dependencies such as Room only after the base app syncs and builds cleanly.

**Tech Stack:** Kotlin, Jetpack Compose, Android Gradle Plugin, Gradle Wrapper, JUnit, Android instrumented tests, coroutine/Flow APIs, Android file intents

---

## Scope Split

The approved spec covers multiple slices that can be delivered incrementally. To keep execution reliable and avoid getting stuck on initial dependency/bootstrap work, this plan covers the first implementation slice:

- Android project bootstrap
- Package boundaries inside a single `app` module
- Bookshelf shell
- TXT parsing foundation
- External `.txt` file import
- Shared reading-anchor model
- Vertical reader mode
- Reader control chrome and persistence hooks

This plan intentionally defers these to the next plan after the app is running:

- Room-backed persistence swap
- Full horizontal page-turn reader
- Source configuration storage
- Advanced metadata cleanup heuristics

## File Map

### Root Build Files

- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create or generate: `gradlew`
- Create or generate: `gradlew.bat`
- Create or generate: `gradle/wrapper/gradle-wrapper.properties`
- Create: `.gitignore`

### Android App Module

- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/values-night/themes.xml`

### App Entry and Theme

- Create: `app/src/main/java/com/longerlsx/storyapp/StoryApplication.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/MainActivity.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/app/AppScreen.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/app/StoryApp.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/app/StoryAppState.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/ui/theme/Color.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/ui/theme/Type.kt`

### Core Model and Common

- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/Book.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/Chapter.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ImportSourceType.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReadingMode.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReadingAnchor.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReadingProgress.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/common/AppDispatchers.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/common/Result.kt`

### Data and Parsing

- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/BookRepository.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/InMemoryBookRepository.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ImportCoordinator.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ImportedBookStorage.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/MetadataExtractor.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/TxtCharsetDetector.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/TxtNormalizer.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterPatterns.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterParser.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/TextContentLoader.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/AnchorStore.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/FileHashCalculator.kt`

### Feature: Import

- Create: `app/src/main/java/com/longerlsx/storyapp/feature/importer/ImportViewModel.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/importer/ExternalImportHandler.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/importer/ImportUiState.kt`

### Feature: Bookshelf

- Create: `app/src/main/java/com/longerlsx/storyapp/feature/bookshelf/BookshelfViewModel.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/bookshelf/BookshelfUiState.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/bookshelf/BookshelfScreen.kt`

### Feature: Reader

- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderViewModel.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderUiState.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTocSheet.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ScrollReaderContent.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/PageReaderPlaceholder.kt`

### Feature: Source Entry Placeholder

- Create: `app/src/main/java/com/longerlsx/storyapp/feature/source/SourceEntryScreen.kt`

### Tests

- Create: `app/src/test/java/com/longerlsx/storyapp/data/book/MetadataExtractorTest.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/data/book/TxtNormalizerTest.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterParserTest.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/data/book/FileHashCalculatorTest.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/data/book/InMemoryBookRepositoryTest.kt`
- Create: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReadingAnchorReducerTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/AppLaunchTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/importer/ExternalImportFlowTest.kt`
- Create: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`
- Create: `app/src/test/resources/fixtures/sample_book_header.txt`
- Create: `app/src/test/resources/fixtures/sample_book_chapters.txt`

### Docs

- Modify: `docs/superpowers/specs/2026-04-15-android-reader-design.md` only if implementation choices diverge materially
- Create: `README.md`

## Task 1: Bootstrap an Offline-Friendly Android Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create or generate: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `README.md`

- [ ] **Step 1: Generate the Android project skeleton**

Use Android Studio to create an Empty Compose Activity project named `story_app` with package `com.longerlsx.storyapp`, `minSdk` 28, and Kotlin DSL build files. Let Android Studio generate the wrapper files instead of hand-rolling them.

- [ ] **Step 2: Pin the toolchain to locally cached versions**

Set the generated wrapper and plugin versions to an offline-friendly combination first:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```toml
# gradle/libs.versions.toml
[versions]
agp = "8.7.3"
kotlin = "2.1.21"
```

Prefer `Gradle 8.9` in `gradle-wrapper.properties` so the wrapper hits the existing local cache.

- [ ] **Step 3: Add machine setup notes to the README**

Document the required shell exports and emulator baseline:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
```

- [ ] **Step 4: Verify the skeleton builds**

Run: `./gradlew help`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "chore: bootstrap android compose project"
```

## Task 2: Establish Package Boundaries and a Runnable App Shell

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/StoryApplication.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/MainActivity.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/app/AppScreen.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/app/StoryApp.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/app/StoryAppState.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/ui/theme/Color.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/ui/theme/Type.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/bookshelf/BookshelfScreen.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/source/SourceEntryScreen.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/AppLaunchTest.kt`

- [ ] **Step 1: Write the failing app launch test**

```kotlin
@Test
fun appLaunchesIntoBookshelf() {
    composeTestRule.onNodeWithText("书架").assertIsDisplayed()
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.AppLaunchTest`

Expected: FAIL because the bookshelf shell is not rendered yet.

- [ ] **Step 3: Implement the app shell**

Keep the first slice dependency-light: use a local `AppScreen` enum and `mutableStateOf` app state instead of bringing in Navigation Compose on day one.

```kotlin
enum class AppScreen {
    BOOKSHELF,
    READER,
    SOURCE_ENTRY
}
```

Render `BookshelfScreen()` by default and add a clearly labeled placeholder path to `SourceEntryScreen()`.

- [ ] **Step 4: Re-run the app launch test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.AppLaunchTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main
git add app/src/androidTest
git commit -m "feat: add runnable app shell and bookshelf entry"
```

## Task 3: Add Core Reader Models and Repository Interfaces

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/Book.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/Chapter.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ImportSourceType.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReadingMode.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReadingAnchor.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReadingProgress.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/BookRepository.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/InMemoryBookRepository.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/InMemoryBookRepositoryTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReadingAnchorReducerTest.kt`

- [ ] **Step 1: Write the failing repository and anchor tests**

```kotlin
@Test
fun saveAndRestoreAnchorReturnsLatestOffset() { /* ... */ }

@Test
fun importedBooksAreReturnedInReverseLastReadOrder() { /* ... */ }
```

- [ ] **Step 2: Run the unit tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*InMemoryBookRepositoryTest" --tests "*ReadingAnchorReducerTest"`

Expected: FAIL because the model and repository types do not exist.

- [ ] **Step 3: Implement the models and in-memory repository**

Use a shared anchor shape that matches the spec:

```kotlin
data class ReadingAnchor(
    val chapterIndex: Int,
    val charOffset: Int
)
```

The repository should expose flows for bookshelf data and methods for:

- saving imported books
- updating `lastReadAt`
- saving/restoring reading progress
- loading chapter lists and chapter text

- [ ] **Step 4: Re-run the unit tests**

Run: `./gradlew testDebugUnitTest --tests "*InMemoryBookRepositoryTest" --tests "*ReadingAnchorReducerTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/longerlsx/storyapp/core
git add app/src/main/java/com/longerlsx/storyapp/data/book
git add app/src/test
git commit -m "feat: add core reader models and repository contracts"
```

## Task 4: Build the TXT Parsing Foundation

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/TxtCharsetDetector.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/TxtNormalizer.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/MetadataExtractor.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterPatterns.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ChapterParser.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/TxtNormalizerTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/MetadataExtractorTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/ChapterParserTest.kt`
- Create: `app/src/test/resources/fixtures/sample_book_header.txt`
- Create: `app/src/test/resources/fixtures/sample_book_chapters.txt`

- [ ] **Step 1: Write failing parser tests from synthetic fixtures**

Use short synthetic fixtures instead of committing the full novel text. Cover:

- UTF-8 BOM stripping
- ad/header noise
- title and author extraction
- `第1章` and `第一章` chapter matching
- preface capture before chapter one

```kotlin
@Test
fun parserPreservesPrefaceBeforeFirstChapter() { /* ... */ }
```

- [ ] **Step 2: Run the parser tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*TxtNormalizerTest" --tests "*MetadataExtractorTest" --tests "*ChapterParserTest"`

Expected: FAIL because the parser classes do not exist yet.

- [ ] **Step 3: Implement minimal parsing utilities**

Keep the first implementation deterministic and regex-driven:

```kotlin
private val chapterRegex = Regex(
    pattern = """^\s*(第[0-9一二三四五六七八九十百千零〇两]+章.*|序章|楔子|尾声)\s*$""",
    option = RegexOption.MULTILINE
)
```

Normalize newlines, strip a leading BOM, and leave unknown text intact instead of aggressively deleting lines.

- [ ] **Step 4: Re-run parser tests**

Run: `./gradlew testDebugUnitTest --tests "*TxtNormalizerTest" --tests "*MetadataExtractorTest" --tests "*ChapterParserTest"`

Expected: PASS

- [ ] **Step 5: Manual sample verification with the user-provided TXT**

Use the local sample only for manual smoke checking:

`/Users/longshengxi/Downloads/《小猫咪在星际监狱也会手慢无？》作者：桃花朋.txt`

Expected: title/author extracted, preface preserved, first chapter detected.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/longerlsx/storyapp/data/book
git add app/src/test
git commit -m "feat: add txt metadata and chapter parsing foundation"
```

## Task 5: Implement Imported File Storage and Duplicate Detection

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ImportedBookStorage.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/FileHashCalculator.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/TextContentLoader.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/FileHashCalculatorTest.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/data/book/InMemoryBookRepositoryTest.kt`

- [ ] **Step 1: Write failing storage tests**

Cover:

- copying an imported file into app-private storage
- stable hash generation for duplicate detection
- loading normalized content from the stored file

```kotlin
@Test
fun sameFileContentProducesSameHash() { /* ... */ }
```

- [ ] **Step 2: Run the storage tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*FileHashCalculatorTest" --tests "*InMemoryBookRepositoryTest"`

Expected: FAIL because storage and hash utilities are missing.

- [ ] **Step 3: Implement private-file storage**

Store imported books under:

`files/books/<book-id>/original.txt`

and keep all app reads pointed at the copied file, never the external URI.

- [ ] **Step 4: Re-run the storage tests**

Run: `./gradlew testDebugUnitTest --tests "*FileHashCalculatorTest" --tests "*InMemoryBookRepositoryTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/longerlsx/storyapp/data/book
git add app/src/test
git commit -m "feat: add imported file storage and duplicate detection"
```

## Task 6: Wire External TXT Import Into the App

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/importer/ImportViewModel.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/importer/ImportUiState.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/importer/ExternalImportHandler.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/ImportCoordinator.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/file_paths.xml`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/importer/ExternalImportFlowTest.kt`

- [ ] **Step 1: Write the failing import flow test**

The instrumented test should simulate an external file intent and assert that:

- the file is accepted when it is `.txt`
- the book is added to the shelf
- the app navigates into the reader

```kotlin
@Test
fun openTxtIntentImportsBookAndNavigatesToReader() { /* ... */ }
```

- [ ] **Step 2: Run the import flow test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.importer.ExternalImportFlowTest`

Expected: FAIL because the app does not yet handle the external intent.

- [ ] **Step 3: Add manifest intent filters and import coordinator**

Accept at least:

- `ACTION_VIEW`
- `ACTION_SEND`

for `text/plain` and `.txt` file flows that real apps may emit. Copy the file immediately, parse it, save it, then navigate directly to the reader.

- [ ] **Step 4: Re-run the import flow test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.importer.ExternalImportFlowTest`

Expected: PASS

- [ ] **Step 5: Manual smoke test with the sample novel**

From a file source or sharing app, open:

`/Users/longshengxi/Downloads/《小猫咪在星际监狱也会手慢无？》作者：桃花朋.txt`

Expected: the book imports, appears on the shelf, and opens directly into the reader.

- [ ] **Step 6: Commit**

```bash
git add app/src/main
git add app/src/androidTest
git commit -m "feat: add external txt import flow"
```

## Task 7: Deliver the Bookshelf and Scroll Reader Vertical Slice

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/bookshelf/BookshelfViewModel.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/bookshelf/BookshelfUiState.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/bookshelf/BookshelfScreen.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderViewModel.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderUiState.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ScrollReaderContent.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderControls.kt`
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderTocSheet.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`

- [ ] **Step 1: Write the failing reader chrome test**

Cover:

- tapping the reader center reveals controls
- the current chapter is shown
- the table of contents can be opened

```kotlin
@Test
fun centerTapShowsReaderControls() { /* ... */ }
```

- [ ] **Step 2: Run the reader chrome test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderChromeTest`

Expected: FAIL because the reader UI does not exist yet.

- [ ] **Step 3: Implement the vertical slice**

The bookshelf should show imported books and recent reading order. The reader should:

- render scrollable chapter text
- show immersive chrome by default
- reveal top/bottom controls on center tap
- open a TOC bottom sheet
- jump between chapters

- [ ] **Step 4: Re-run the reader chrome test**

Run: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.longerlsx.storyapp.feature.reader.ReaderChromeTest`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/longerlsx/storyapp/feature/bookshelf
git add app/src/main/java/com/longerlsx/storyapp/feature/reader
git add app/src/androidTest
git commit -m "feat: add bookshelf and scroll reader vertical slice"
```

## Task 8: Persist Reading Anchors Across Background and Restart

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/data/book/AnchorStore.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/data/book/InMemoryBookRepository.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderViewModel.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderUiState.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReadingAnchorReducerTest.kt`
- Test: `app/src/androidTest/java/com/longerlsx/storyapp/feature/reader/ReaderChromeTest.kt`

- [ ] **Step 1: Extend the failing tests for persistence**

Cover:

- saving an updated anchor after user movement settles
- restoring the latest anchor after ViewModel recreation
- switching away from and back to the app without losing position

```kotlin
@Test
fun latestAnchorWinsAfterMultipleUpdates() { /* ... */ }
```

- [ ] **Step 2: Run the persistence tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "*ReadingAnchorReducerTest"`

Expected: FAIL because no anchor persistence exists yet.

- [ ] **Step 3: Implement throttled anchor saving**

Persist:

- `bookId`
- `chapterIndex`
- `charOffset`

Use lightweight local storage for this first slice so restart recovery works before Room is introduced.

- [ ] **Step 4: Re-run persistence tests**

Run: `./gradlew testDebugUnitTest --tests "*ReadingAnchorReducerTest"`

Expected: PASS

- [ ] **Step 5: Manual restart verification**

Expected:

- read into the sample novel
- send the app to background
- relaunch the app
- reopen on the same chapter and near the same character offset

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/longerlsx/storyapp/data/book
git add app/src/main/java/com/longerlsx/storyapp/feature/reader
git add app/src/test
git commit -m "feat: persist reading anchors across restart"
```

## Task 9: Add a Horizontal Reader Placeholder on the Shared Anchor Model

**Files:**
- Create: `app/src/main/java/com/longerlsx/storyapp/feature/reader/PageReaderPlaceholder.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/core/model/ReadingMode.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/core/model/ReaderSettings.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderViewModel.kt`
- Modify: `app/src/main/java/com/longerlsx/storyapp/feature/reader/ReaderScreen.kt`
- Test: `app/src/test/java/com/longerlsx/storyapp/feature/reader/ReadingAnchorReducerTest.kt`

- [ ] **Step 1: Write the failing mode-switch test**

```kotlin
@Test
fun switchingModesKeepsSameAnchor() { /* ... */ }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "*ReadingAnchorReducerTest"`

Expected: FAIL because mode switching is not modeled yet.

- [ ] **Step 3: Add a horizontal reader placeholder**

Do not implement the final paging engine in this plan. Add:

- a mode toggle in the reader controls
- a placeholder horizontal reader surface
- shared anchor preservation across mode switches

This protects the architecture before the real page-turn renderer is built.

- [ ] **Step 4: Re-run the mode-switch test**

Run: `./gradlew testDebugUnitTest --tests "*ReadingAnchorReducerTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/longerlsx/storyapp/core/model
git add app/src/main/java/com/longerlsx/storyapp/feature/reader
git add app/src/test
git commit -m "feat: add shared reading mode toggle and horizontal placeholder"
```

## Task 10: Stabilize, Document, and Prepare the Room/Page-Turn Follow-Up Plan

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-04-15-android-reader-design.md` if needed
- Create: `docs/superpowers/plans/2026-04-15-android-reader-persistence-followup.md`

- [ ] **Step 1: Run the full verification suite**

Run:

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew :app:assembleDebug
```

Expected: all commands pass.

- [ ] **Step 2: Update the README with actual run instructions**

Document:

- required env vars
- emulator target
- how to test external `.txt` imports
- the sample novel path used for manual verification

- [ ] **Step 3: Write the follow-up plan**

The next plan should cover:

- swapping the repository to Room
- restoring the most recent book on cold start
- replacing the horizontal placeholder with a real page-turn implementation
- source-entry persistence

- [ ] **Step 4: Commit**

```bash
git add README.md docs/superpowers
git commit -m "docs: record setup and next-phase plan"
```

## Execution Notes

- Prefer Android Studio for initial sync if command-line Gradle reports network fetches for dependencies not already cached.
- Do not add Room or Navigation Compose early if the base app has not synced successfully yet.
- Keep parser fixtures synthetic and short; use the user-provided novel only for local manual smoke tests.
- If the initial AGP/Kotlin combination misses cache and forces network, downgrade only after confirming the missing artifact and recording the reason in the commit message or README.
- The first runnable milestone is complete when a `.txt` file can be imported into the app and opened in the vertical reader with anchor restoration working across restart.
