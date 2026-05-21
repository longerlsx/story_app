package com.longerlsx.storyapp.feature.reader.tts

import java.util.Locale

data class ReaderTtsRawVoice(
    val name: String,
    val languageTag: String?,
    val displayName: String?,
)

data class ReaderTtsVoiceResolution(
    val visibleOptions: List<ReaderTtsVoiceOption>,
    val resolvedVoiceName: String?,
    val usingSystemDefaultFallback: Boolean,
)

object ReaderTtsVoiceCatalog {
    fun resolve(
        availableVoices: List<ReaderTtsRawVoice>,
        persistedVoiceName: String?,
    ): ReaderTtsVoiceResolution {
        val visibleOptions = availableVoices
            .filter(::isMainlandChineseVoice)
            .groupBy { canonicalFamilyKey(it.name) }
            .values
            .mapNotNull(::preferredVoiceInFamily)
            .sortedWith(compareBy({ it.displayName.orEmpty() }, { it.name }))
            .map(::toVoiceOption)

        val resolvedVoiceName = persistedVoiceName
            ?.takeIf { selectedName -> visibleOptions.any { it.name == selectedName } }
        return ReaderTtsVoiceResolution(
            visibleOptions = visibleOptions,
            resolvedVoiceName = resolvedVoiceName,
            usingSystemDefaultFallback = persistedVoiceName != null && resolvedVoiceName == null,
        )
    }

    private fun preferredVoiceInFamily(voices: List<ReaderTtsRawVoice>): ReaderTtsRawVoice? {
        return voices.minWithOrNull(
            compareBy<ReaderTtsRawVoice>(
                { if (it.name.endsWith("-local", ignoreCase = true)) 0 else 1 },
                { if (it.name.endsWith("-network", ignoreCase = true)) 1 else 0 },
                { simplifiedScriptRank(it.languageTag) },
                { it.name },
            ),
        )
    }

    private fun simplifiedScriptRank(languageTag: String?): Int {
        val locale = languageTag
            ?.takeIf(String::isNotBlank)
            ?.let(Locale::forLanguageTag)
        return when (locale?.script?.uppercase(Locale.ROOT)) {
            "HANS" -> 0
            "" -> 1
            null -> 1
            else -> 2
        }
    }

    private fun toVoiceOption(voice: ReaderTtsRawVoice): ReaderTtsVoiceOption {
        val label = listOfNotNull(
            voice.displayName?.takeIf(String::isNotBlank),
            voice.name.takeIf(String::isNotBlank),
        ).joinToString(" · ")
        return ReaderTtsVoiceOption(
            name = voice.name,
            displayName = label,
        )
    }

    private fun isMainlandChineseVoice(voice: ReaderTtsRawVoice): Boolean {
        val locale = voice.languageTag
            ?.takeIf(String::isNotBlank)
            ?.let(Locale::forLanguageTag)
        val language = locale?.language?.lowercase(Locale.ROOT)
        val region = locale?.country?.uppercase(Locale.ROOT)
        val script = locale?.script?.uppercase(Locale.ROOT)
        if (region == "CN" && (language == "zh" || language == "cmn")) {
            return true
        }
        if (region.isNullOrBlank() && language == "zh" && script != "HANT") {
            return true
        }

        val normalizedName = voice.name.lowercase(Locale.ROOT)
        return normalizedName.contains("cmn-cn") || normalizedName.contains("zh-cn")
    }

    private fun canonicalFamilyKey(name: String): String {
        return name
            .lowercase(Locale.ROOT)
            .removeSuffix("-local")
            .removeSuffix("-network")
    }
}
