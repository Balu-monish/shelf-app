@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.ebooksplayer.shelf.ui.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme

/**
 * Font size / line spacing / font family / theme controls, shared between
 * the reader's own "Aa" dialog and the Settings screen's reading-defaults
 * section - both edit the same persisted [EpubPreferences].
 */
@Composable
fun ReadingPreferencesControls(
    preferences: EpubPreferences,
    onPreferencesChange: (EpubPreferences) -> Unit,
) {
    Column {
        Text("Font size", style = MaterialTheme.typography.labelMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = {
                val next = ((preferences.fontSize ?: 1.0) - 0.1).coerceAtLeast(0.6)
                onPreferencesChange(preferences.copy(fontSize = next))
            }) { Text("A-") }
            Text("${((preferences.fontSize ?: 1.0) * 100).toInt()}%")
            TextButton(onClick = {
                val next = ((preferences.fontSize ?: 1.0) + 0.1).coerceAtMost(2.5)
                onPreferencesChange(preferences.copy(fontSize = next))
            }) { Text("A+") }
        }

        Text(
            "Line spacing",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = {
                val next = ((preferences.lineHeight ?: 1.2) - 0.1).coerceAtLeast(1.0)
                onPreferencesChange(preferences.copy(lineHeight = next))
            }) { Text("−") }
            Text("%.1fx".format(preferences.lineHeight ?: 1.2))
            TextButton(onClick = {
                val next = ((preferences.lineHeight ?: 1.2) + 0.1).coerceAtMost(2.5)
                onPreferencesChange(preferences.copy(lineHeight = next))
            }) { Text("+") }
        }

        Text(
            "Font",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FontFamilyChip("Default", null, preferences, onPreferencesChange)
            FontFamilyChip("Serif", FontFamily.SERIF, preferences, onPreferencesChange)
            FontFamilyChip("Sans", FontFamily.SANS_SERIF, preferences, onPreferencesChange)
            FontFamilyChip("Cursive", FontFamily.CURSIVE, preferences, onPreferencesChange)
            FontFamilyChip("Fantasy", FontFamily.FANTASY, preferences, onPreferencesChange)
            FontFamilyChip("Monospace", FontFamily.MONOSPACE, preferences, onPreferencesChange)
        }

        Text(
            "Theme",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeChip("Light", Theme.LIGHT, preferences, onPreferencesChange)
            ThemeChip("Sepia", Theme.SEPIA, preferences, onPreferencesChange)
            ThemeChip("Dark", Theme.DARK, preferences, onPreferencesChange)
        }
    }
}

@Composable
private fun FontFamilyChip(
    label: String,
    fontFamily: FontFamily?,
    preferences: EpubPreferences,
    onPreferencesChange: (EpubPreferences) -> Unit,
) {
    FilterChip(
        selected = preferences.fontFamily == fontFamily,
        onClick = { onPreferencesChange(preferences.copy(fontFamily = fontFamily)) },
        label = { Text(label) },
    )
}

@Composable
private fun ThemeChip(
    label: String,
    theme: Theme,
    preferences: EpubPreferences,
    onPreferencesChange: (EpubPreferences) -> Unit,
) {
    FilterChip(
        selected = preferences.theme == theme,
        onClick = { onPreferencesChange(preferences.copy(theme = theme)) },
        label = { Text(label) },
    )
}
