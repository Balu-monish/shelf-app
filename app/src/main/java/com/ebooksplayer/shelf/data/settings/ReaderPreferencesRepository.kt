@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.ebooksplayer.shelf.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme

/**
 * Persists the reader's font/theme/line-spacing choices as a global default
 * that applies to whichever book is opened, rather than resetting every
 * session or being scoped to one book. Only the subset of EpubPreferences
 * the reader's own UI exposes is persisted.
 */
@Singleton
class ReaderPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shelf_reader_prefs", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(readPreferences())
    val preferences: StateFlow<EpubPreferences> = _preferences.asStateFlow()

    fun update(preferences: EpubPreferences) {
        _preferences.value = preferences
        prefs.edit {
            preferences.fontSize?.let { putFloat(KEY_FONT_SIZE, it.toFloat()) } ?: remove(KEY_FONT_SIZE)
            preferences.fontFamily?.let { putString(KEY_FONT_FAMILY, it.name) } ?: remove(KEY_FONT_FAMILY)
            preferences.lineHeight?.let { putFloat(KEY_LINE_HEIGHT, it.toFloat()) } ?: remove(KEY_LINE_HEIGHT)
            preferences.theme?.let { putString(KEY_THEME, it.name) } ?: remove(KEY_THEME)
        }
    }

    private fun readPreferences(): EpubPreferences {
        val fontSize = if (prefs.contains(KEY_FONT_SIZE)) prefs.getFloat(KEY_FONT_SIZE, 1f).toDouble() else 1.0
        val fontFamily = prefs.getString(KEY_FONT_FAMILY, null)?.let { FontFamily(it) }
        val lineHeight = if (prefs.contains(KEY_LINE_HEIGHT)) prefs.getFloat(KEY_LINE_HEIGHT, 1.2f).toDouble() else 1.2
        val theme = prefs.getString(KEY_THEME, null)
            ?.let { runCatching { Theme.valueOf(it) }.getOrNull() }
            ?: Theme.LIGHT

        return EpubPreferences(
            fontSize = fontSize,
            fontFamily = fontFamily,
            lineHeight = lineHeight,
            theme = theme,
        )
    }

    companion object {
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_FONT_FAMILY = "font_family"
        private const val KEY_LINE_HEIGHT = "line_height"
        private const val KEY_THEME = "theme"
    }
}
