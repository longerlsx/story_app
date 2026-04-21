package com.longerlsx.storyapp.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSystemBarStyleResolverTest {

    @Test
    fun usesDarkIconsForLightReaderThemes() {
        val style = ReaderSystemBarStyleResolver.resolve(
            ReaderThemePreset.PAPER.palette(),
        )

        assertTrue(style.useDarkIcons)
        assertFalse(style.enforceNavigationBarContrast)
    }

    @Test
    fun usesLightIconsForDarkReaderThemes() {
        val style = ReaderSystemBarStyleResolver.resolve(
            ReaderThemePreset.AMOLED.palette(),
        )

        assertFalse(style.useDarkIcons)
        assertFalse(style.enforceNavigationBarContrast)
    }
}
