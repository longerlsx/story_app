package com.longerlsx.storyapp.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.longerlsx.storyapp.feature.reader.tts.ReaderTtsCharacterRange

internal object ReaderHighlightAnnotator {
    fun annotate(
        text: String,
        textStartCharOffset: Int,
        textEndCharOffset: Int,
        highlightRange: ReaderTtsCharacterRange?,
        highlightColor: Color,
    ): AnnotatedString {
        if (highlightRange == null || textEndCharOffset <= textStartCharOffset) {
            return AnnotatedString(text)
        }
        val localHighlightStart = maxOf(highlightRange.startCharOffset, textStartCharOffset) - textStartCharOffset
        val localHighlightEnd = minOf(highlightRange.endCharOffset, textEndCharOffset) - textStartCharOffset
        if (localHighlightStart >= localHighlightEnd) {
            return AnnotatedString(text)
        }
        return buildAnnotatedString {
            append(text)
            addStyle(
                style = SpanStyle(background = highlightColor),
                start = localHighlightStart,
                end = localHighlightEnd,
            )
        }
    }
}
