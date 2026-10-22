package com.longerlsx.storyapp.core.model

data class ReaderBundledVoice(
    val name: String,
    val displayName: String,
    val speakerId: Int,
)

object ReaderBundledVoices {
    const val DEFAULT_NAME = "piper:aishell3:ssb1020"

    val options = listOf(
        ReaderBundledVoice(DEFAULT_NAME, "C · SSB1020", 30),
        ReaderBundledVoice("piper:aishell3:ssb0863", "F · SSB0863", 151),
    )

    fun resolve(name: String?): ReaderBundledVoice = options.firstOrNull { it.name == name } ?: options.first()
}
