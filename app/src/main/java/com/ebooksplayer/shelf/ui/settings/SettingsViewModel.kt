@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.ebooksplayer.shelf.ui.settings

import com.ebooksplayer.shelf.data.db.entity.BookFormat
import com.ebooksplayer.shelf.data.settings.AppSettingsRepository
import com.ebooksplayer.shelf.data.settings.DriveFolderMapping
import com.ebooksplayer.shelf.data.settings.ReaderPreferencesRepository
import com.ebooksplayer.shelf.data.settings.ThemeMode
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.navigator.epub.EpubPreferences

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val readerPreferencesRepository: ReaderPreferencesRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = appSettingsRepository.themeMode
    fun setThemeMode(mode: ThemeMode) = appSettingsRepository.setThemeMode(mode)

    val readingPreferences: StateFlow<EpubPreferences> = readerPreferencesRepository.preferences
    fun setReadingPreferences(preferences: EpubPreferences) = readerPreferencesRepository.update(preferences)

    val driveFolders: StateFlow<List<DriveFolderMapping>> = appSettingsRepository.driveFolders

    fun addDriveFolder(folderName: String, format: BookFormat) {
        val trimmed = folderName.trim()
        if (trimmed.isEmpty()) return
        appSettingsRepository.setDriveFolders(driveFolders.value + DriveFolderMapping(trimmed, format))
    }

    fun removeDriveFolder(mapping: DriveFolderMapping) {
        appSettingsRepository.setDriveFolders(driveFolders.value - mapping)
    }
}
