package com.longerlsx.storyapp.feature.reader

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File

internal enum class ReaderPrimaryActionSlot {
    DIRECTORY,
    APPEARANCE,
    SETTINGS,
}

internal fun resetStoryAppState(context: Context) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    File(context.filesDir, "books").deleteRecursively()
    File(context.filesDir, "reader-anchors.properties").delete()
    File(context.filesDir, "reader-settings.properties").delete()
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

internal fun UiDevice.waitForReaderTopBar(
    titleHint: String,
    timeoutMs: Long = 2_000,
): Boolean {
    return wait(Until.hasObject(By.desc("阅读器顶部栏")), timeoutMs) ||
        wait(Until.hasObject(By.descContains(titleHint)), timeoutMs) ||
        wait(Until.hasObject(By.desc("更多")), timeoutMs)
}

internal fun UiDevice.tapPrimaryAction(slot: ReaderPrimaryActionSlot): Boolean {
    val label = when (slot) {
        ReaderPrimaryActionSlot.DIRECTORY -> "目录"
        ReaderPrimaryActionSlot.APPEARANCE -> if (hasObject(By.text("夜间"))) "夜间" else "日间"
        ReaderPrimaryActionSlot.SETTINGS -> "设置"
    }
    return tapActionLabel(label) || click(
        when (slot) {
        ReaderPrimaryActionSlot.DIRECTORY -> (displayWidth * 0.18f).toInt()
        ReaderPrimaryActionSlot.APPEARANCE -> displayWidth / 2
        ReaderPrimaryActionSlot.SETTINGS -> (displayWidth * 0.82f).toInt()
        },
        (displayHeight * 0.92f).toInt(),
    )
}

internal fun UiDevice.tapChapterAction(previous: Boolean): Boolean {
    val label = if (previous) "上一章" else "下一章"
    return tapActionLabel(label) || click(
        if (previous) {
            (displayWidth * 0.18f).toInt()
        } else {
            (displayWidth * 0.82f).toInt()
        },
        (displayHeight * 0.845f).toInt(),
    )
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
    val scrollMode = wait(Until.findObject(By.descContains("阅读模式：滚动")), 2_000)
        ?: wait(Until.findObject(By.text("滚动")), 2_000)
        ?: return false
    scrollMode.click()
    if (!wait(Until.hasObject(By.desc("阅读模式：滚动，已选中")), 2_000)) {
        return false
    }
    tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS)
    wait(Until.gone(By.text("亮度")), 2_000)
    click(displayWidth / 2, displayHeight / 2)
    waitForIdle()
    return true
}

internal fun UiDevice.ensurePageMode(): Boolean {
    if (!openReaderSettings()) {
        return false
    }
    val pageMode = wait(Until.findObject(By.descContains("阅读模式：翻页")), 2_000)
        ?: wait(Until.findObject(By.text("翻页")), 2_000)
        ?: return false
    pageMode.click()
    if (!wait(Until.hasObject(By.desc("阅读模式：翻页，已选中")), 2_000)) {
        return false
    }
    tapPrimaryAction(ReaderPrimaryActionSlot.SETTINGS)
    wait(Until.gone(By.text("亮度")), 2_000)
    click(displayWidth / 2, displayHeight / 2)
    waitForIdle()
    return true
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
            try {
                target.click()
                waitForIdle()
                return true
            } catch (_: StaleObjectException) {
                waitForIdle()
            }
        }
    }
    return false
}
