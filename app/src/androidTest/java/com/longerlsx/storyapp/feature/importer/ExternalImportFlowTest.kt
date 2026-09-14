package com.longerlsx.storyapp.feature.importer

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.longerlsx.storyapp.MainActivity
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.ImportSourceType
import com.longerlsx.storyapp.core.model.ReadingAnchor
import com.longerlsx.storyapp.core.model.ReadingMode
import com.longerlsx.storyapp.core.model.ReadingProgress
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalImportFlowTest {

    @Test
    fun freshActivityChecksStoredBookWithoutResettingAnAlreadyOpenReader() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<StoryApplication>()
        application.awaitLibraryReady()
        val first = application.importCoordinator.importTxt(
            "initial-route-first.txt", "第1章 开始\n初始路由甲书正文。".toByteArray(), ImportSourceType.LOCAL_FILE,
        ).book
        val second = application.importCoordinator.importTxt(
            "initial-route-second.txt", "第1章 开始\n初始路由乙书正文。".toByteArray(), ImportSourceType.LOCAL_FILE,
        ).book
        val firstProgress = ReadingProgress(
            first.id, ReadingAnchor(0, 0), ReadingMode.PAGE, System.currentTimeMillis(),
        )
        application.persistReadingProgress(firstProgress).join()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(device.wait(Until.hasObject(By.textContains("初始路由甲书正文")), 8_000))
            application.persistReadingProgress(
                firstProgress.copy(bookId = second.id, updatedAt = firstProgress.updatedAt + 1),
            ).join()
            device.waitForIdle()
            assertTrue(device.hasObject(By.textContains("初始路由甲书正文")))
        }

        // Simulate a stale saved selection while the application's Ready snapshot still names a book.
        application.anchorStore.setLastOpenedBookId("missing-initial-route-book")
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(device.wait(Until.hasObject(By.text("导入 TXT")), 8_000))
        }
    }

    @Test
    fun localSelectionAndExternalIntentReadTheSamePayloadAndReportTheSameFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "shared-import.txt")
        try {
            val content = "第1章 开始\n两种导入入口的正文。".toByteArray()
            file.writeBytes(content)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/plain")

            val local = ExternalImportHandler.prepareImport(context, uri).getOrThrow()
            val external = requireNotNull(ExternalImportHandler.prepareImport(context, intent)).getOrThrow()
            assertEquals("shared-import.txt", local.fileName)
            assertEquals(local.fileName, external.fileName)
            assertArrayEquals(content, local.bytes)
            assertArrayEquals(content, external.bytes)

            file.delete()
            val localFailure = ExternalImportHandler.prepareImport(context, uri).exceptionOrNull()
            val externalFailure = ExternalImportHandler.prepareImport(context, intent)?.exceptionOrNull()
            assertTrue(localFailure?.message?.contains("无法读取文件") == true)
            assertEquals(localFailure?.message, externalFailure?.message)
        } finally {
            file.delete()
        }
    }

    @Test
    fun unreadableViewIntentShowsAnActionableImportError() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            setDataAndType(Uri.parse("file:///missing-story-app-import.txt"), "text/plain")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            assertTrue(device.wait(Until.hasObject(By.textContains("无法读取文件")), 8_000))
        }
    }

    @Test
    fun openTxtIntentImportsBookAndNavigatesToReader() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val importFile = File(context.cacheDir, "external-import.txt").apply {
            writeText(
                """
                《外部导入测试》
                作者：测试作者

                第1章 开始
                外部导入正文。
                """.trimIndent(),
            )
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            importFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            setDataAndType(uri, "text/plain")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        ActivityScenario.launch<MainActivity>(intent).use {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val imported = device.wait(Until.hasObject(By.textContains("外部导入正文")), 8_000)

            assertTrue(imported)
        }
    }

    @Test
    fun unreadableViewIntentReturnsNullInsteadOfCrashing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("file:///sdcard/Download/story-app-unreadable-import.txt"),
                "text/plain",
            )
        }

        assertNull(ExternalImportHandler.extractPayload(context, intent))
    }
}
