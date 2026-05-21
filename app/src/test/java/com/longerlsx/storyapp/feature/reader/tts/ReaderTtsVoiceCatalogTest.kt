package com.longerlsx.storyapp.feature.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTtsVoiceCatalogTest {

    @Test
    fun keepsOnlyMainlandChineseVoicesAndPrefersLocalVariants() {
        val resolution = ReaderTtsVoiceCatalog.resolve(
            availableVoices = listOf(
                ReaderTtsRawVoice(
                    name = "cmn-cn-x-ccc-network",
                    languageTag = "zh-CN",
                    displayName = "Chinese (China)",
                ),
                ReaderTtsRawVoice(
                    name = "cmn-cn-x-ccc-local",
                    languageTag = "zh-CN",
                    displayName = "Chinese (China)",
                ),
                ReaderTtsRawVoice(
                    name = "cmn-cn-x-ssa-local",
                    languageTag = "cmn-CN",
                    displayName = "Chinese (China)",
                ),
                ReaderTtsRawVoice(
                    name = "cmn-tw-x-ctc-local",
                    languageTag = "zh-TW",
                    displayName = "Chinese (Taiwan)",
                ),
                ReaderTtsRawVoice(
                    name = "en-us-x-sfg-network",
                    languageTag = "en-US",
                    displayName = "English (United States)",
                ),
            ),
            persistedVoiceName = null,
        )

        assertEquals(
            listOf("cmn-cn-x-ccc-local", "cmn-cn-x-ssa-local"),
            resolution.visibleOptions.map(ReaderTtsVoiceOption::name),
        )
        assertTrue(resolution.visibleOptions.all { it.displayName.contains("China") })
        assertFalse(resolution.usingSystemDefaultFallback)
        assertEquals(null, resolution.resolvedVoiceName)
    }

    @Test
    fun fallsBackToSystemDefaultWhenPersistedVoiceIsFilteredOut() {
        val resolution = ReaderTtsVoiceCatalog.resolve(
            availableVoices = listOf(
                ReaderTtsRawVoice(
                    name = "cmn-cn-x-ssa-local",
                    languageTag = "zh-CN",
                    displayName = "Chinese (China)",
                ),
            ),
            persistedVoiceName = "cmn-tw-x-ctc-local",
        )

        assertEquals(listOf("cmn-cn-x-ssa-local"), resolution.visibleOptions.map { it.name })
        assertEquals(null, resolution.resolvedVoiceName)
        assertTrue(resolution.usingSystemDefaultFallback)
    }

    @Test
    fun reportsEmptyVisibleChoicesWhenNoMainlandChineseVoiceExists() {
        val resolution = ReaderTtsVoiceCatalog.resolve(
            availableVoices = listOf(
                ReaderTtsRawVoice(
                    name = "cmn-tw-x-ctc-local",
                    languageTag = "zh-TW",
                    displayName = "Chinese (Taiwan)",
                ),
                ReaderTtsRawVoice(
                    name = "en-us-x-sfg-network",
                    languageTag = "en-US",
                    displayName = "English (United States)",
                ),
            ),
            persistedVoiceName = "cmn-tw-x-ctc-local",
        )

        assertTrue(resolution.visibleOptions.isEmpty())
        assertEquals(null, resolution.resolvedVoiceName)
        assertTrue(resolution.usingSystemDefaultFallback)
    }

    @Test
    fun keepsXiaomiSimplifiedChineseVoicesWithoutRegionCode() {
        val resolution = ReaderTtsVoiceCatalog.resolve(
            availableVoices = listOf(
                ReaderTtsRawVoice(
                    name = "en",
                    languageTag = "en",
                    displayName = "英语",
                ),
                ReaderTtsRawVoice(
                    name = "zh",
                    languageTag = "zh",
                    displayName = "中文",
                ),
                ReaderTtsRawVoice(
                    name = "zh",
                    languageTag = "zh-Hans",
                    displayName = "中文 (简体中文)",
                ),
                ReaderTtsRawVoice(
                    name = "zh",
                    languageTag = "zh-Hant",
                    displayName = "中文 (繁体中文)",
                ),
            ),
            persistedVoiceName = null,
        )

        assertEquals(listOf("zh"), resolution.visibleOptions.map { it.name })
        assertTrue(resolution.visibleOptions.single().displayName.contains("简体中文"))
        assertFalse(resolution.visibleOptions.single().displayName.contains("繁体中文"))
    }
}
