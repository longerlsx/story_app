package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTtsNotificationPermissionPolicyTest {

    @Test
    fun startsImmediatelyBeforeRuntimeNotificationPermissionIsRequired() {
        assertEquals(
            ReaderTtsNotificationPermissionDecision.StartImmediately,
            ReaderTtsNotificationPermissionPolicy.resolve(
                sdkInt = 32,
                notificationPermissionGranted = false,
            ),
        )
    }

    @Test
    fun startsImmediatelyWhenRuntimeNotificationPermissionIsGranted() {
        assertEquals(
            ReaderTtsNotificationPermissionDecision.StartImmediately,
            ReaderTtsNotificationPermissionPolicy.resolve(
                sdkInt = 33,
                notificationPermissionGranted = true,
            ),
        )
    }

    @Test
    fun requestsPermissionBeforeStartingOnAndroidThirteenAndAboveWhenPermissionIsMissing() {
        assertEquals(
            ReaderTtsNotificationPermissionDecision.RequestPermissionFirst,
            ReaderTtsNotificationPermissionPolicy.resolve(
                sdkInt = 33,
                notificationPermissionGranted = false,
            ),
        )
        assertEquals(
            ReaderTtsNotificationPermissionDecision.RequestPermissionFirst,
            ReaderTtsNotificationPermissionPolicy.resolve(
                sdkInt = 36,
                notificationPermissionGranted = false,
            ),
        )
    }
}
