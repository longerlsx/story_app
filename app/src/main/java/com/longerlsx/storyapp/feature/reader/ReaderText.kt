package com.longerlsx.storyapp.feature.reader

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.em

internal fun TextStyle.withReaderParagraphIndent(indentFirstLine: Boolean = true): TextStyle = copy(
    textIndent = TextIndent(firstLine = if (indentFirstLine) 2.em else 0.em),
)

@Composable
internal fun ReaderSingleLineText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}
