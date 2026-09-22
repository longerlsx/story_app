package com.longerlsx.storyapp.core.model

object ReaderTtsSpeechRates {
    val options: List<Float> = listOf(0.85f, 1f, 1.3f, 1.45f, 2f)

    fun label(rate: Float): String = "${rate.toString().removeSuffix(".0")}×"

    fun fromStoredValue(value: Float?): Float =
        value?.takeIf { it.isFinite() && it > 0f }
            ?.let { if (it == 1.15f) 1f else it }
            ?: 1f
}
