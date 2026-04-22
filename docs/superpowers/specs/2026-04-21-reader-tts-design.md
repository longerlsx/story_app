# Reader Background TTS Design

## Overview

This slice adds background text-to-speech playback to the existing Android reader without turning the app into a full media player.

The reader already owns:

- local TXT import
- anchor-based progress restore
- scroll mode and page mode
- immersive vs chrome-visible reader states
- persistent reading settings

The missing capability is a reliable "read aloud" mode that can keep speaking after the user leaves the reader, turns the screen off, or switches to another app.

The target shape is a lightweight background-listening feature:

- foreground-service based playback
- system TextToSpeech engine
- pause and resume controls in the notification
- reader-owned start position, highlight, and follow behavior

## In Scope

- Add a reader TTS session that can continue while the app is backgrounded or the screen is off
- Add a circular read-aloud toggle inside the reader chrome
- Show the same toggle in immersive mode only while TTS is currently active
- Open a dedicated TTS settings panel via long-press on the TTS toggle
- Support current-system-engine voice selection, speech rate, pitch, and timer presets
- Default the timer preference to `no timer` until the user explicitly chooses another preset
- Pause timer countdown while playback is paused
- Start playback from the current top visible reading line
- Support long-press on body text to restart playback from the pressed location
- Use forward-only absorption when the long-pressed position lands on whitespace, title text, or punctuation-only spans
- Keep manual page turns and manual scrolling from changing the current TTS queue
- Stop the active TTS session when the user performs explicit chapter navigation such as TOC selection, previous chapter, next chapter, or cross-book opening
- Auto-follow the active playback position in both scroll mode and page mode
- Highlight the current spoken range when timing callbacks are available
- Fall back to segment-level highlight when range timing is unavailable
- Auto-pause on audio-focus loss and auto-resume when focus returns
- Stop at timer expiry or when the book reaches its final readable segment

## Out Of Scope

- Previous or next controls in the notification
- Full MediaSession lock-screen, headset-button, or car integration
- Engine installation or download flows
- Switching between multiple TTS engines inside the app
- Natural-language prosody optimization or "humanized" sentence delivery
- Word-by-word or character-by-character speech queueing
- Resuming an old TTS queue after explicit chapter navigation
- Auto-resume after process death or device reboot
- Cross-book playback continuity

## Design Goals

- Keep the voice behavior predictable and slightly mechanical rather than expressive
- Avoid adding persistent reader chrome when TTS is off
- Make TTS progress precise enough for believable highlight, resume, and follow behavior without fragmenting playback into unnatural tiny chunks
- Preserve reader-first interaction rules: the reading surface remains the source of truth for where playback begins
- Treat background playback as a real user-facing foreground task, not a hidden background job

## Interaction Model

### Entry And Visibility

The reader gets one circular TTS toggle.

Rules:

- when TTS is off, the toggle appears only in the `CHROME_VISIBLE` primary action row
- when TTS is on, the toggle remains available in immersive mode as a separate overlay so the user can stop it quickly
- the toggle does not render inside `SETTINGS_EXPANDED`, `DIRECTORY_OPEN`, or the dedicated TTS settings panel
- when the idle toggle is visible in `CHROME_VISIBLE`, it should expose a lightweight discoverability hint that long-press opens TTS settings, without adding a second persistent settings button
- long-pressing the toggle opens the TTS settings panel
- opening the settings panel does not implicitly start playback

### Settings Panel

The TTS settings panel contains:

- voice list from the current system TTS engine
- speech rate
- pitch
- timer options: `no timer`, `15 / 30 / 60 / 90 / 120`

Behavior rules:

- if the user never picks a timer, playback starts with `no timer`
- while playback is active, changing voice, rate, or pitch applies from the next queued short sentence block
- while playback is active, changing timer presets updates remaining time immediately
- if the current engine exposes only one voice, the panel still shows the voice row but it behaves as a read-only single-option choice
- once the user changes voice, rate, pitch, or timer preset, later playback sessions must reuse those saved values instead of resetting back to first-run defaults

Mounting rules:

- the TTS settings panel is its own expanded reader chrome state, separate from the existing generic reader settings sheet
- opening TTS settings enters a dedicated `TTS_SETTINGS_EXPANDED` mode
- `CHROME_VISIBLE`, `SETTINGS_EXPANDED`, `DIRECTORY_OPEN`, and `TTS_SETTINGS_EXPANDED` are mutually exclusive
- the TTS panel should not be nested inside the generic reader settings panel
- explicit panel-close actions inside `TTS_SETTINGS_EXPANDED` return to `CHROME_VISIBLE`
- center-tap dismissal while `TTS_SETTINGS_EXPANDED` is open collapses the full reader chrome to `READING_ONLY`
- opening directory or generic settings while TTS settings is open should replace `TTS_SETTINGS_EXPANDED` rather than stack another panel on top

### Active Playback Feedback

The TTS toggle and notification should surface timer state explicitly.

Rules:

- when playback is active and a timer is set, the in-reader TTS toggle shows active playback plus remaining time
- when playback is active and no timer is set, the in-reader TTS toggle shows an active `reading aloud` state without a countdown
- the notification shows remaining time when a timer is set
- the notification shows a stable active-state label such as `朗读中` when no timer is set
- the UI should not make `no timer` look like a missing or broken countdown

### Pause And Stop Semantics

Pause and stop are different product states.

Pause applies only to:

- user-triggered pause
- audio-focus-loss pause

Pause rules:

- pause preserves the current TTS session
- resume continues the same TTS session
- pause keeps the existing timer budget frozen rather than consuming it

Stop applies to:

- user toggling playback off
- TOC selection
- previous or next chapter actions
- opening another book
- timer expiry
- reaching the final readable segment

Stop rules:

- stop ends the active TTS session
- a later playback start after stop creates a new session
- when that new session starts from the reader, it starts from the current visible top-line location
- in-reader navigation-triggered stops should surface concise local feedback so the user understands playback ended rather than paused
- that feedback should make it clear a later fresh start begins from the current visible top-line location

### Start And Stop Rules

Starting playback:

- starts from the current visible top-line reading location
- uses the reader mode currently on screen only to resolve that starting location
- does not depend on whether the user is in scroll mode or page mode

Stopping playback:

- happens immediately when the user taps the toggle off
- happens automatically when the timer reaches zero
- happens automatically when the final readable segment of the book completes
- happens automatically when app navigation switches from the currently playing `bookId` to a different `bookId` via bookshelf or import flow
- happens automatically when the user selects a chapter from TOC or uses explicit previous/next chapter navigation

Non-interrupting actions:

- manual scrolling
- manual page turns
- switching between page mode and scroll mode
- backgrounding the app
- turning the screen off

Those actions may change what the reader shows, but they do not change the current TTS queue.

Queue protection rule:

- manual scroll and manual page-turn gestures may temporarily suspend auto-follow, but they must not restart, rebuild, pause, or stop the active TTS queue

### Long-Press Restart

When TTS is already active, long-pressing body text:

- cancels the current speech queue
- finds the nearest valid readable offset at or after the pressed location
- rebuilds the queue starting from that new offset
- keeps the existing remaining timer instead of resetting it

Discoverability rule:

- once playback first becomes active in a session, the reader may show a lightweight one-shot hint that long-pressing body text restarts playback from that position

Forward-only absorption rules:

- if the pressed position hits whitespace, skip forward to the next readable character
- if it hits a chapter heading or other non-body element, skip forward until body text begins
- if it hits punctuation-only spans, skip forward until actual readable content begins
- if no later readable text exists in the book, stop playback

## Background Playback Model

### Foreground Service

Playback runs inside a foreground service with the `mediaPlayback` service type.

Implications:

- playback can continue after the app is backgrounded
- playback can continue while the screen is off
- the user always has a system-visible notification while playback is active
- the app must start the service from a foreground user action path

### Notification Behavior

The notification contains:

- current book title
- current chapter title or playback summary
- remaining time when a timer is active
- pause or resume action

Rules:

- tapping the notification body returns to the active book's reader
- the notification does not expose previous or next actions
- notification pause and resume use the same state transitions as in-reader pause and resume
- if no timer is active, the notification should show an explicit active-state label instead of an empty timer slot

Android 13+ note:

- the app should request notification permission before promising notification-drawer controls
- if permission is denied, foreground playback may still run, but the app should clearly explain that drawer controls may be unavailable
- the degraded path should have explicit reader-local copy, not an implicit silent fallback

### Audio Focus

The app requests audio focus for spoken playback.

Rules:

- focus loss pauses playback
- focus gain attempts to resume playback if the pause reason was audio-focus loss rather than a user stop
- this auto-pause and auto-resume path shares the same internal pause and resume state machine as manual controls

### App-Scoped Playback Owner

The playback controller must be owned above the Compose reader tree.

Rules:

- the long-lived TTS controller is created once at application scope and exposed from `StoryApplication`
- `StoryApp` reads that app-scoped controller from `StoryApplication` and passes it explicitly into `ReaderScreen`
- `ReaderScreen` and other composables only observe and send commands; they do not own controller lifetime
- the foreground service binds to that same app-scoped controller state instead of to a per-screen instance
- recomposition, screen recreation, and temporary absence of the reader UI must not recreate the active controller or lose the service binding

## Text Segmentation And Position Model

### Queue Shape

Playback is queued as short sentence blocks, not words and not whole paragraphs.

Recommended shape:

- split first on sentence-ending punctuation
- optionally split long sentences again on lighter punctuation such as commas or semicolons when the chunk becomes too long
- preserve original text indices for every queued segment

Every queued segment carries:

- `bookId`
- `chapterIndex`
- `segmentId`
- `startCharOffset`
- `endCharOffset`
- `spokenText`

### Why Short Sentence Blocks

This is the chosen balance because it:

- sounds more natural than word-level queueing
- avoids the queue explosion of character-level queueing
- still allows a character-based progress model for highlight and resume
- keeps long-press restart, auto-follow, and pause behavior on one shared coordinate system

### Character-Level Positioning

Although speech is queued as short sentence blocks, reader progress is tracked at character granularity.

The system should maintain:

- the current segment
- the last confirmed spoken character range if the engine provides timing callbacks
- the next recoverable character or range after pause

Product promise:

- resume should start from the next recoverable character or range after confirmed progress
- the app should not promise exact ear-perceived sample-level precision across all engines

This keeps the feature honest while still targeting a near-character-level user experience.

### Highlight Behavior

Primary mode:

- use `UtteranceProgressListener.onRangeStart(...)` to highlight the currently spoken subrange inside the queued segment when available

Fallback mode:

- highlight the entire current short sentence block when range timing is unavailable or unreliable

### Start-Location Resolution

The reader needs two location resolvers:

- visible-top-line resolver for starting playback
- long-press body-text resolver for restart-from-here

Both resolvers must map view-local UI positions back to the same book-wide coordinate system:

- `chapterIndex`
- `charOffset`

That shared mapping is what keeps:

- queue construction
- progress save
- highlight
- auto-follow
- long-press restart

aligned across both reading modes.

Implementation constraints:

- the start-location mapper should stay as a pure, deterministic unit with direct unit-test coverage for top-line resolution, forward-only absorption, and chapter/offset mapping
- the reader UI must expose explicit hooks for both scroll mode and page mode so long-press gestures and current-top-line sampling can feed real character offsets into that mapper
- this work should not be hidden inside ad-hoc Compose gesture code with no test seam

## Follow Behavior

When playback is active, the reader should auto-follow the spoken location.

### Scroll Mode

- auto-scroll to keep the spoken range visible
- if the user manually scrolls, suppress auto-follow for `10` seconds
- if no additional manual scroll happens during that window, jump immediately back to the current spoken location

### Page Mode

- auto-turn pages when the spoken range crosses onto a new page
- if the user manually pages, suppress auto-follow for `10` seconds
- if no additional manual page action happens during that window, jump immediately back to the page containing the current spoken location

### Shared Rules

- manual scroll and manual page-turn gestures never change the current TTS queue
- explicit chapter navigation stops the active TTS session rather than leaving audio on an old location
- explicit chapter-navigation stops do not arm the `10` second follow snap-back path because the old playback session has already ended
- the `10` second suppression timer does not pause just because the screen is off or the app is backgrounded
- resuming follow after suppression does not animate; it jumps directly to the active spoken location

## Reader State Integration

The TTS feature introduces a reader-wide session state that the UI can observe.

Minimum states:

- `OFF`
- `STARTING`
- `PLAYING`
- `PAUSED_BY_USER`
- `PAUSED_BY_AUDIO_FOCUS`
- `STOPPED_BY_USER`
- `STOPPED_BY_NAVIGATION`
- `STOPPED_BY_TIMER`
- `STOPPED_AT_BOOK_END`
- `FAILED`

The reader uses that state to decide:

- whether the immersive toggle is visible
- which label or icon the chrome-visible toggle shows
- whether highlight should render
- whether follow suppression is currently active

## Settings Persistence

Persisted reader settings should now also include:

- selected voice name
- speech rate
- pitch
- most recently selected timer preset

Session-start rule:

- every new playback session should initialize from the last persisted voice, rate, pitch, and timer preset
- first-run defaults apply only until the user changes them for the first time
- `no timer` is a first-class persisted preference, not the absence of a value

Non-persisted session state:

- remaining timer for the current playback session
- current queue
- current spoken range
- current pause reason

Those session details should reset when the app loses the active playback session or the user opens another book.

Required runtime snapshot fields:

- current queued segment identity
- last confirmed spoken character range
- next recoverable character or range after pause
- current pause reason when the session is paused
- current chapter-facing playback summary for notification updates

Those runtime fields are the source of truth for:

- pause and resume
- long-press restart after a pause
- notification content updates
- highlight continuity while the UI reconnects to an active background session

Lifecycle rule:

- the app-scoped controller owns this runtime snapshot as the single source of truth
- the foreground service mirrors and executes controller state, but it does not become the authority for resume, highlight, or recoverable-position semantics

## Failure Handling

If system TTS is unavailable or initialization fails:

- do not enter active playback state
- surface a reader-local error message
- keep the toggle visually off

If the selected voice disappears between sessions:

- fall back to the engine default voice
- keep the rest of the TTS settings intact

If timing callbacks never arrive:

- continue playback
- fall back to short-segment highlight
- keep pause and resume based on the last safely known recoverable range

If foreground service startup fails:

- cancel playback startup
- keep the UI out of fake `playing` state
- show a concise reader-local error message

## Testing Strategy

### Unit Tests

- TTS settings persistence and legacy defaults
- short-sentence segmentation and punctuation splitting
- character-range mapping and next-recoverable-position logic
- pause and resume state transitions
- stop vs pause state transitions
- follow-suppression timing behavior
- audio-focus reducer behavior
- runtime startup failure handling
- timing-missing highlight fallback behavior

### Instrumented Tests

- toggle visibility rules in immersive vs chrome-visible states
- long-pressing the TTS toggle opens the settings panel
- active timed playback shows remaining time on the in-reader toggle
- active untimed playback shows a stable active-state label without countdown
- starting playback shows the active toggle in immersive mode
- long-pressing body text restarts playback from a later absorbed body position
- scroll mode auto-follow pauses for `10` seconds after manual scroll and then snaps back
- page mode auto-follow pauses for `10` seconds after manual paging and then snaps back
- switching reading modes does not stop playback
- opening another book stops the previous book's playback
- TOC selection stops the active session
- previous and next chapter actions stop the active session
- notification pause and resume stay wired to the same session state
- notification shows remaining time for timed sessions and active-state text for untimed sessions
- denied notification permission shows the degraded-path message without fake playback state

## Acceptance Criteria

This slice is successful when:

- the user can start read-aloud from the current visible top line
- playback continues while the app is backgrounded or the screen is off
- the notification exposes pause and resume for the active book
- the notification shows remaining time when timed and active-state text when untimed
- long-pressing body text while TTS is active restarts from the absorbed later body position
- timer presets behave as expected, `no timer` is supported, and pause does not consume timer budget
- manual scroll and manual paging temporarily suppress follow without changing the queue
- follow snaps back to the current spoken location after `10` seconds of user inactivity
- explicit chapter navigation and opening another book stop the active playback session
- voice, rate, pitch, and timer settings persist across app relaunch, including `no timer`
- timing-capable devices show sub-segment highlight, while non-capable devices still show stable segment-level progress
