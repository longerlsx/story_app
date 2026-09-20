package com.longerlsx.storyapp.feature.reader

import android.content.Context
import com.longerlsx.storyapp.StoryApplication
import com.longerlsx.storyapp.core.model.ReaderSettings
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import kotlinx.coroutines.runBlocking

internal enum class ReaderPrimaryActionSlot {
    DIRECTORY,
    APPEARANCE,
    SETTINGS,
    TTS,
}

internal fun resetStoryAppState(context: Context) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val application = context.applicationContext as? StoryApplication
    runBlocking {
        application?.awaitLibraryReady()
        application?.readerSettingsStore?.awaitPendingWrites()
    }
    File(context.filesDir, "books").deleteRecursively()
    File(context.filesDir, "reader-anchors.properties").delete()
    File(context.filesDir, "reader-settings.properties").delete()
    File(context.filesDir, "listening-progress.properties").delete()
    application?.let { application ->
        runBlocking {
            application.bookRepository.clearForTests()
            application.readerSettingsStore.save(ReaderSettings())
            application.readerSettingsStore.awaitPendingWrites()
        }
        application.resetReaderTtsRuntimeForTests()
    }
    instrumentation.waitForIdleSync()
}

internal fun UiDevice.revealReaderChrome(
    controlLabel: String,
    attempts: Int = 3,
): Boolean {
    repeat(attempts) {
        if (hasObject(By.text(controlLabel)) || hasObject(By.desc(controlLabel))) {
            return true
        }
        click(displayWidth / 2, displayHeight / 2)
        waitForIdle()
        if (
            wait(Until.hasObject(By.text(controlLabel)), 1_000) ||
            wait(Until.hasObject(By.desc(controlLabel)), 1_000)
        ) {
            return true
        }
    }
    return hasObject(By.text(controlLabel)) || hasObject(By.desc(controlLabel))
}

internal fun UiDevice.clickObjectCenter(target: UiObject2): Boolean {
    return try {
        val bounds = target.visibleBounds
        if (bounds.width() > 0 && bounds.height() > 0) {
            click(bounds.centerX(), bounds.centerY())
        } else {
            target.click()
        }
        waitForIdle()
        true
    } catch (_: StaleObjectException) {
        waitForIdle()
        false
    }
}

internal fun UiDevice.waitForReaderTopBar(
    titleHint: String,
    timeoutMs: Long = 2_000,
): Boolean {
    return wait(Until.hasObject(By.desc("阅读器顶部栏")), timeoutMs) ||
        wait(Until.hasObject(By.descContains(titleHint)), timeoutMs)
}

internal fun UiDevice.tapPrimaryAction(slot: ReaderPrimaryActionSlot): Boolean {
    fun resolveLabel() = when (slot) {
        ReaderPrimaryActionSlot.DIRECTORY -> "目录"
        ReaderPrimaryActionSlot.APPEARANCE -> if (hasObject(By.text("夜间"))) "夜间" else "日间"
        ReaderPrimaryActionSlot.SETTINGS -> "设置"
        ReaderPrimaryActionSlot.TTS -> when {
            hasObject(By.textContains("暂停朗读")) -> "暂停朗读"
            hasObject(By.textContains("停止朗读")) -> "停止朗读"
            hasObject(By.textContains("继续朗读")) -> "继续朗读"
            else -> "朗读"
        }
    }
    if (tapActionLabel(resolveLabel())) {
        return true
    }
    click(displayWidth / 2, displayHeight / 2)
    waitForIdle()
    if (tapActionLabel(resolveLabel())) {
        return true
    }
    val clicked = click(
        when (slot) {
            ReaderPrimaryActionSlot.DIRECTORY -> (displayWidth * 0.125f).toInt()
            ReaderPrimaryActionSlot.APPEARANCE -> (displayWidth * 0.375f).toInt()
            ReaderPrimaryActionSlot.SETTINGS -> (displayWidth * 0.625f).toInt()
            ReaderPrimaryActionSlot.TTS -> (displayWidth * 0.875f).toInt()
        },
        (displayHeight * 0.92f).toInt(),
    )
    waitForIdle()
    return clicked
}

internal fun UiDevice.tapChapterAction(previous: Boolean): Boolean {
    val label = if (previous) "上一章" else "下一章"
    if (tapActionLabel(label)) {
        return true
    }
    click(displayWidth / 2, displayHeight / 2)
    waitForIdle()
    if (tapActionLabel(label)) {
        return true
    }
    val clicked = click(
        if (previous) {
            (displayWidth * 0.18f).toInt()
        } else {
            (displayWidth * 0.82f).toInt()
        },
        (displayHeight * 0.845f).toInt(),
    )
    waitForIdle()
    return clicked
}

internal fun UiDevice.longPressActionLabel(label: String): Boolean {
    repeat(4) {
        val target = wait(Until.findObject(By.desc(label)), 1_000)
            ?: wait(Until.findObject(By.text(label)), 1_000)
        if (target != null) {
            try {
                target.longClick()
                waitForIdle()
                return true
            } catch (_: StaleObjectException) {
                waitForIdle()
            }
        }
    }
    return false
}

internal fun UiDevice.openReaderSettings(): Boolean {
    if (!revealReaderChrome("设置")) {
        return false
    }
    tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS)
    waitForIdle()
    return wait(Until.hasObject(By.text("亮度")), 2_000)
}

internal fun UiDevice.ensureScrollMode(): Boolean {
    if (!openReaderSettings()) {
        return false
    }
    val scrollMode = findSettingsOption(
        descContains = "阅读模式：滚动",
        visibleText = "滚动",
    )
        ?: return false
    if (!clickObjectCenter(scrollMode)) {
        return false
    }
    val selectedInSheet = wait(Until.hasObject(By.desc("阅读模式：滚动，已选中")), 1_200)
    tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS)
    wait(Until.gone(By.text("亮度")), 2_000)
    hideReaderChromeIfVisible()
    return selectedInSheet
}

internal fun UiDevice.ensurePageMode(): Boolean {
    if (!openReaderSettings()) {
        return false
    }
    val pageMode = findSettingsOption(
        descContains = "阅读模式：翻页",
        visibleText = "翻页",
    )
        ?: return false
    if (!clickObjectCenter(pageMode)) {
        return false
    }
    val selectedInSheet = wait(Until.hasObject(By.desc("阅读模式：翻页，已选中")), 1_200)
    tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS)
    wait(Until.gone(By.text("亮度")), 2_000)
    hideReaderChromeIfVisible()
    return selectedInSheet
}

internal fun UiDevice.scrollUntilTextVisible(
    text: String,
    attempts: Int = 18,
): Boolean {
    repeat(attempts) {
        if (hasObject(By.text(text)) || hasObject(By.textContains(text))) {
            return true
        }
        swipe(
            displayWidth / 2,
            (displayHeight * 0.8f).toInt(),
            displayWidth / 2,
            (displayHeight * 0.25f).toInt(),
            24,
        )
        waitForIdle()
    }
    return hasObject(By.text(text)) || hasObject(By.textContains(text))
}

private fun UiDevice.waitForActionLabel(label: String): Boolean {
    repeat(4) {
        if (
            wait(Until.hasObject(By.desc(label)), 1_000) ||
            wait(Until.hasObject(By.text(label)), 1_000)
        ) {
            return true
        }
        waitForIdle()
    }
    return false
}

private fun UiDevice.tapActionLabel(label: String): Boolean {
    repeat(4) {
        val target = wait(Until.findObject(By.desc(label)), 1_000)
            ?: wait(Until.findObject(By.text(label)), 1_000)
        if (target != null) {
            if (clickObjectCenter(target)) {
                return true
            }
        }
    }
    return false
}

private fun UiDevice.hideReaderChromeIfVisible() {
    if (
        hasObject(By.text("设置")) ||
        hasObject(By.desc("设置")) ||
        hasObject(By.text("目录")) ||
        hasObject(By.desc("目录")) ||
        hasObject(By.text("上一章")) ||
        hasObject(By.desc("上一章")) ||
        hasObject(By.text("下一章")) ||
        hasObject(By.desc("下一章"))
    ) {
        click(displayWidth / 2, displayHeight / 2)
        waitForIdle()
        wait(Until.gone(By.text("设置")), 2_000)
    }
}

private fun UiDevice.findSettingsOption(
    descContains: String,
    visibleText: String,
): androidx.test.uiautomator.UiObject2? {
    repeat(5) {
        val option = wait(Until.findObject(By.descContains(descContains)), 600)
            ?: wait(Until.findObject(By.text(visibleText)), 600)
        if (option != null) {
            return option
        }
        swipe(
            displayWidth / 2,
            (displayHeight * 0.82f).toInt(),
            displayWidth / 2,
            (displayHeight * 0.42f).toInt(),
            20,
        )
        waitForIdle()
    }
    return null
}
