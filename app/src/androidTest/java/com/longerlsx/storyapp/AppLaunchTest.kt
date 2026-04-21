package com.longerlsx.storyapp

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.longerlsx.storyapp.feature.reader.resetStoryAppState
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @Test
    fun appLaunchesIntoBookshelf() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        resetStoryAppState(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

        assertNotNull("Expected launcher activity to be available", launchIntent)

        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(launchIntent)

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val bookshelfVisible = device.wait(Until.hasObject(By.text("书架")), 5_000)

        assertTrue("Expected bookshelf label to be visible on app launch", bookshelfVisible)
    }
}
