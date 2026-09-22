package com.longerlsx.storyapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// UI typography uses the system's Chinese font fallback. Reader body layout owns its own styles.
val StoryTypography = Typography(
    displayLarge = storyTextStyle(48, 60),
    displayMedium = storyTextStyle(42, 52),
    displaySmall = storyTextStyle(36, 46),
    headlineLarge = storyTextStyle(32, 42, FontWeight.Bold),
    headlineMedium = storyTextStyle(26, 36, FontWeight.SemiBold),
    headlineSmall = storyTextStyle(22, 32, FontWeight.SemiBold),
    titleLarge = storyTextStyle(22, 32, FontWeight.Medium),
    titleMedium = storyTextStyle(18, 28, FontWeight.SemiBold),
    titleSmall = storyTextStyle(15, 22, FontWeight.Medium),
    bodyLarge = storyTextStyle(16, 26),
    bodyMedium = storyTextStyle(14, 22),
    bodySmall = storyTextStyle(13, 20),
    labelLarge = storyTextStyle(14, 20, FontWeight.Medium),
    labelMedium = storyTextStyle(12, 18, FontWeight.Medium),
    labelSmall = storyTextStyle(12, 18, FontWeight.Medium),
)

private fun storyTextStyle(
    fontSize: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)
