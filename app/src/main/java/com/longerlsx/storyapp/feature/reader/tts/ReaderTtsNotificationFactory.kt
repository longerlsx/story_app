package com.longerlsx.storyapp.feature.reader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.longerlsx.storyapp.R

class ReaderTtsNotificationFactory(
    private val context: Context,
) {
    private val notificationManager by lazy {
        context.getSystemService(NotificationManager::class.java)
    }

    fun ensureChannel() {
        val existing = notificationManager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            return
        }
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reader_tts_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    fun build(runtimeState: ReaderTtsRuntimeState): Notification {
        val summaryLine = runtimeState.currentPlaybackSummary.orEmpty()
        val actionIsResume =
            runtimeState.playbackState == ReaderTtsSessionState.PAUSED_BY_USER ||
                runtimeState.playbackState == ReaderTtsSessionState.PAUSED_BY_AUDIO_FOCUS
        val remainingTimeLabel = runtimeState.remainingTimerMillis
            ?.let(ReaderTtsTimeLabelFormatter::formatRemainingMillis)
        val stateLine = when {
            actionIsResume && !remainingTimeLabel.isNullOrBlank() -> "已暂停 · $remainingTimeLabel"
            actionIsResume -> "已暂停"
            !remainingTimeLabel.isNullOrBlank() -> remainingTimeLabel
            else -> runtimeState.activeStateLabel
        }
        val contentText = listOfNotNull(
            summaryLine.takeIf(String::isNotBlank),
            stateLine.takeIf(String::isNotBlank),
        ).joinToString(" · ")
            .ifBlank { runtimeState.activeStateLabel }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(
                runtimeState.currentBookTitle
                    ?: context.getString(R.string.reader_tts_notification_title_fallback),
            )
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOnlyAlertOnce(true)
            .setOngoing(runtimeState.isOngoingSession())
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        runtimeState.currentBookId?.let { bookId ->
            builder.setContentIntent(
                ReaderTtsIntentFactory.createNotificationContentPendingIntent(
                    context = context,
                    bookId = bookId,
                ),
            )
        }

        builder.addAction(
            NotificationCompat.Action(
                if (actionIsResume) {
                    android.R.drawable.ic_media_play
                } else {
                    android.R.drawable.ic_media_pause
                },
                context.getString(
                    if (actionIsResume) {
                        R.string.reader_tts_resume
                    } else {
                        R.string.reader_tts_pause
                    },
                ),
                if (actionIsResume) {
                    ReaderTtsIntentFactory.createResumeActionPendingIntent(context)
                } else {
                    ReaderTtsIntentFactory.createPauseActionPendingIntent(context)
                },
            ),
        )
        builder.addAction(
            NotificationCompat.Action(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.reader_tts_stop),
                ReaderTtsIntentFactory.createStopActionPendingIntent(context),
            ),
        )

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "reader_tts_playback"
        const val NOTIFICATION_ID = 4107
    }
}
