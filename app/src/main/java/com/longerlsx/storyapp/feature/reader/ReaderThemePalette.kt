package com.longerlsx.storyapp.feature.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class ReaderThemePalette(
    val background: Color,
    val surface: Color,
    val content: Color,
)

// Control colors do not participate in the reader's text/layout identity.
val ReaderThemePalette.accent: Color
    get() = if (background.luminance() < 0.3f) Color(0xFFB0D0BA) else Color(0xFF355A4B)
val ReaderThemePalette.onAccent: Color
    get() = if (background.luminance() < 0.3f) Color(0xFF183B2C) else Color.White
val ReaderThemePalette.subtleContent: Color get() = content.copy(alpha = 0.76f)
val ReaderThemePalette.outline: Color get() = content.copy(alpha = 0.22f)

fun ReaderThemePreset.palette(): ReaderThemePalette {
    return when (this) {
        ReaderThemePreset.PAPER -> ReaderThemePalette(
            background = Color(0xFFF7F1E7),
            surface = Color(0xFFFFFBF5),
            content = Color(0xFF241E1B),
        )

        ReaderThemePreset.SEPIA -> ReaderThemePalette(
            background = Color(0xFFF0E2C8),
            surface = Color(0xFFF7ECD8),
            content = Color(0xFF3B2D1D),
        )

        ReaderThemePreset.MINT -> ReaderThemePalette(
            background = Color(0xFFEAF3E4),
            surface = Color(0xFFF3F8EE),
            content = Color(0xFF243121),
        )

        ReaderThemePreset.SKY -> ReaderThemePalette(
            background = Color(0xFFEAF1F8),
            surface = Color(0xFFF5F8FC),
            content = Color(0xFF1F2C39),
        )

        ReaderThemePreset.CHARCOAL -> ReaderThemePalette(
            background = Color(0xFF141820),
            surface = Color(0xFF1D2330),
            content = Color(0xFFE6EAF2),
        )

        ReaderThemePreset.SLATE -> ReaderThemePalette(
            background = Color(0xFF1A1F27),
            surface = Color(0xFF252C37),
            content = Color(0xFFE2E7F0),
        )

        ReaderThemePreset.AMOLED -> ReaderThemePalette(
            background = Color(0xFF090909),
            surface = Color(0xFF121212),
            content = Color(0xFFF1F1F1),
        )
    }
}
