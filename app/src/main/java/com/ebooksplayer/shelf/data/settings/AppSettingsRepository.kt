package com.ebooksplayer.shelf.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ebooksplayer.shelf.data.db.entity.BookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System"),
}

data class DriveFolderMapping(val folderName: String, val format: BookFormat)

private val DEFAULT_DRIVE_FOLDERS = listOf(
    DriveFolderMapping("Dedrm books", BookFormat.EPUB),
    DriveFolderMapping("drm free books", BookFormat.EPUB),
    DriveFolderMapping("de drm audio books", BookFormat.M4B),
)

/**
 * App-wide preferences - plain SharedPreferences rather than DataStore, since
 * these are a handful of small values, not worth a whole extra dependency.
 */
@Singleton
class AppSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shelf_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    private fun readThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private val _driveFolders = MutableStateFlow(readDriveFolders())
    val driveFolders: StateFlow<List<DriveFolderMapping>> = _driveFolders.asStateFlow()

    fun setDriveFolders(folders: List<DriveFolderMapping>) {
        _driveFolders.value = folders
        val array = JSONArray()
        folders.forEach { mapping ->
            array.put(
                JSONObject().apply {
                    put("folderName", mapping.folderName)
                    put("format", mapping.format.name)
                },
            )
        }
        prefs.edit { putString(KEY_DRIVE_FOLDERS, array.toString()) }
    }

    private fun readDriveFolders(): List<DriveFolderMapping> {
        val stored = prefs.getString(KEY_DRIVE_FOLDERS, null) ?: return DEFAULT_DRIVE_FOLDERS
        return runCatching {
            val array = JSONArray(stored)
            (0 until array.length()).map { i ->
                val entry = array.getJSONObject(i)
                DriveFolderMapping(
                    folderName = entry.getString("folderName"),
                    format = BookFormat.valueOf(entry.getString("format")),
                )
            }
        }.getOrDefault(DEFAULT_DRIVE_FOLDERS)
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DRIVE_FOLDERS = "drive_folders"
    }
}
