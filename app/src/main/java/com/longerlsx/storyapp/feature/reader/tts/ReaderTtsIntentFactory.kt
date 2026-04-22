package com.longerlsx.storyapp.feature.reader.tts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.longerlsx.storyapp.MainActivity
import com.longerlsx.storyapp.core.model.ReaderTtsSettings
import com.longerlsx.storyapp.core.model.ReaderTtsTimerPreset

object ReaderTtsIntentFactory {
    const val ACTION_START = "com.longerlsx.storyapp.reader.tts.START"
    const val ACTION_PAUSE = "com.longerlsx.storyapp.reader.tts.PAUSE"
    const val ACTION_RESUME = "com.longerlsx.storyapp.reader.tts.RESUME"
    const val ACTION_STOP = "com.longerlsx.storyapp.reader.tts.STOP"
    const val ACTION_RESTART = "com.longerlsx.storyapp.reader.tts.RESTART"
    const val ACTION_UPDATE_SETTINGS = "com.longerlsx.storyapp.reader.tts.UPDATE_SETTINGS"
    const val ACTION_OPEN_READER = "com.longerlsx.storyapp.reader.tts.OPEN_READER"

    private const val EXTRA_BOOK_ID = "reader_tts_book_id"
    private const val EXTRA_BOOK_TITLE = "reader_tts_book_title"
    private const val EXTRA_CHAPTER_INDEX = "reader_tts_chapter_index"
    private const val EXTRA_CHAR_OFFSET = "reader_tts_char_offset"
    private const val EXTRA_CHAPTER_SUMMARY = "reader_tts_chapter_summary"
    private const val EXTRA_ACTIVE_STATE_LABEL = "reader_tts_active_state_label"
    private const val EXTRA_VOICE_NAME = "reader_tts_voice_name"
    private const val EXTRA_SPEECH_RATE = "reader_tts_speech_rate"
    private const val EXTRA_PITCH = "reader_tts_pitch"
    private const val EXTRA_TIMER_PRESET = "reader_tts_timer_preset"

    fun startService(context: Context, request: ReaderTtsStartRequest): Intent {
        return Intent(context, ReaderTtsService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_BOOK_ID, request.bookId)
            putExtra(EXTRA_BOOK_TITLE, request.bookTitle)
            putExtra(EXTRA_CHAPTER_INDEX, request.chapterIndex)
            putExtra(EXTRA_CHAR_OFFSET, request.charOffset)
            putExtra(EXTRA_CHAPTER_SUMMARY, request.chapterTitleOrSummary)
            putExtra(EXTRA_ACTIVE_STATE_LABEL, request.activeStateLabel)
        }
    }

    fun pauseService(context: Context): Intent {
        return Intent(context, ReaderTtsService::class.java).apply {
            action = ACTION_PAUSE
        }
    }

    fun restartService(context: Context, request: ReaderTtsStartRequest): Intent {
        return Intent(context, ReaderTtsService::class.java).apply {
            action = ACTION_RESTART
            putExtra(EXTRA_BOOK_ID, request.bookId)
            putExtra(EXTRA_BOOK_TITLE, request.bookTitle)
            putExtra(EXTRA_CHAPTER_INDEX, request.chapterIndex)
            putExtra(EXTRA_CHAR_OFFSET, request.charOffset)
            putExtra(EXTRA_CHAPTER_SUMMARY, request.chapterTitleOrSummary)
            putExtra(EXTRA_ACTIVE_STATE_LABEL, request.activeStateLabel)
        }
    }

    fun resumeService(context: Context): Intent {
        return Intent(context, ReaderTtsService::class.java).apply {
            action = ACTION_RESUME
        }
    }

    fun stopService(context: Context): Intent {
        return Intent(context, ReaderTtsService::class.java).apply {
            action = ACTION_STOP
        }
    }

    fun updateSettingsService(
        context: Context,
        settings: ReaderTtsSettings,
    ): Intent {
        return Intent(context, ReaderTtsService::class.java).apply {
            action = ACTION_UPDATE_SETTINGS
            putExtra(EXTRA_VOICE_NAME, settings.voiceName)
            putExtra(EXTRA_SPEECH_RATE, settings.speechRate)
            putExtra(EXTRA_PITCH, settings.pitch)
            putExtra(EXTRA_TIMER_PRESET, settings.timerPreset.storedValue)
        }
    }

    fun extractStartRequest(intent: Intent?): ReaderTtsStartRequest? {
        if (intent?.action != ACTION_START) {
            return null
        }
        return extractPlaybackRequest(intent)
    }

    fun extractRestartRequest(intent: Intent?): ReaderTtsStartRequest? {
        if (intent?.action != ACTION_RESTART) {
            return null
        }
        return extractPlaybackRequest(intent)
    }

    private fun extractPlaybackRequest(intent: Intent): ReaderTtsStartRequest? {
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: return null
        val bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: return null
        val chapterTitleOrSummary = intent.getStringExtra(EXTRA_CHAPTER_SUMMARY) ?: return null
        val activeStateLabel = intent.getStringExtra(EXTRA_ACTIVE_STATE_LABEL) ?: return null
        return ReaderTtsStartRequest(
            bookId = bookId,
            bookTitle = bookTitle,
            chapterIndex = intent.getIntExtra(EXTRA_CHAPTER_INDEX, 0),
            charOffset = intent.getIntExtra(EXTRA_CHAR_OFFSET, 0),
            chapterTitleOrSummary = chapterTitleOrSummary,
            activeStateLabel = activeStateLabel,
        )
    }

    fun extractSettings(intent: Intent?): ReaderTtsSettings? {
        if (intent?.action != ACTION_UPDATE_SETTINGS) {
            return null
        }
        return ReaderTtsSettings(
            voiceName = intent.getStringExtra(EXTRA_VOICE_NAME),
            speechRate = intent.getFloatExtra(EXTRA_SPEECH_RATE, 1.0f),
            pitch = intent.getFloatExtra(EXTRA_PITCH, 1.0f),
            timerPreset = ReaderTtsTimerPreset.fromStoredValue(
                intent.getStringExtra(EXTRA_TIMER_PRESET),
            ) ?: ReaderTtsTimerPreset.NoTimer,
        )
    }

    fun createPauseActionPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getService(
            context,
            1001,
            pauseService(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun createResumeActionPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getService(
            context,
            1002,
            resumeService(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun createStopActionPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getService(
            context,
            1004,
            stopService(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun createNotificationContentPendingIntent(
        context: Context,
        bookId: String,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_READER
            putExtra(EXTRA_BOOK_ID, bookId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            1003,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun extractOpenReaderBookId(intent: Intent?): String? {
        if (intent?.action != ACTION_OPEN_READER) {
            return null
        }
        return intent.getStringExtra(EXTRA_BOOK_ID)
    }
}
