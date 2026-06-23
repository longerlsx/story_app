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
        val primaryAction = ReaderTtsNotificationActionResolver.resolvePrimaryAction(
            runtimeState.playbackState,
        )
        val contentText = ReaderTtsNotificationTextResolver.resolveContentText(runtimeState)

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
                if (primaryAction == ReaderTtsNotificationPrimaryAction.RESUME) {
                    android.R.drawable.ic_media_play
                } else {
                    android.R.drawable.ic_media_pause
                },
                context.getString(
                    if (primaryAction == ReaderTtsNotificationPrimaryAction.RESUME) {
                        R.string.reader_tts_resume
                    } else {
                        R.string.reader_tts_pause
                    },
                ),
                if (primaryAction == ReaderTtsNotificationPrimaryAction.RESUME) {
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
