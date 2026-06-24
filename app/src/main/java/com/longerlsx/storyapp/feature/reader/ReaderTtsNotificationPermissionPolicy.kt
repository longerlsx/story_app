package com.longerlsx.storyapp.feature.reader

enum class ReaderTtsNotificationPermissionDecision {
    StartImmediately,
    RequestPermissionFirst,
}

object ReaderTtsNotificationPermissionPolicy {
    private const val ANDROID_13_API = 33

    fun resolve(
        sdkInt: Int,
        notificationPermissionGranted: Boolean,
    ): ReaderTtsNotificationPermissionDecision {
        return if (sdkInt >= ANDROID_13_API && !notificationPermissionGranted) {
            ReaderTtsNotificationPermissionDecision.RequestPermissionFirst
        } else {
            ReaderTtsNotificationPermissionDecision.StartImmediately
        }
    }
}
