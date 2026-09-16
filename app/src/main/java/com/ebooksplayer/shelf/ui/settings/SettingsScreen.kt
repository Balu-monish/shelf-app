@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.ebooksplayer.shelf.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ebooksplayer.shelf.data.db.entity.BookFormat
import com.ebooksplayer.shelf.data.settings.DriveFolderMapping
import com.ebooksplayer.shelf.data.settings.ThemeMode
import com.ebooksplayer.shelf.ui.reader.ReadingPreferencesControls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val readingPreferences by viewModel.readingPreferences.collectAsState()
    val driveFolders by viewModel.driveFolders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            SectionTitle("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            SectionTitle("Reading defaults")
            Text(
                "Applied to any book you open that hasn't been customized individually.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ReadingPreferencesControls(
                preferences = readingPreferences,
                onPreferencesChange = viewModel::setReadingPreferences,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            SectionTitle("Drive sync folders")
            Text(
                "Folder names under \"My Drive\" that Drive sync looks in.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            DriveFolderSection(
                folders = driveFolders,
                onAdd = viewModel::addDriveFolder,
                onRemove = viewModel::removeDriveFolder,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun DriveFolderSection(
    folders: List<DriveFolderMapping>,
    onAdd: (String, BookFormat) -> Unit,
    onRemove: (DriveFolderMapping) -> Unit,
) {
    var newFolderName by remember { mutableStateOf("") }
    var newFolderFormat by remember { mutableStateOf(BookFormat.EPUB) }

    Column {
        folders.forEach { mapping ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(mapping.folderName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (mapping.format == BookFormat.EPUB) "Books (.epub)" else "Audiobooks (.m4b)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = { onRemove(mapping) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove folder")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = newFolderName,
                onValueChange = { newFolderName = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Folder name") },
                singleLine = true,
            )
            IconButton(onClick = {
                onAdd(newFolderName, newFolderFormat)
                newFolderName = ""
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add folder")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = newFolderFormat == BookFormat.EPUB,
                onClick = { newFolderFormat = BookFormat.EPUB },
                label = { Text("Books") },
            )
            FilterChip(
                selected = newFolderFormat == BookFormat.M4B,
                onClick = { newFolderFormat = BookFormat.M4B },
                label = { Text("Audiobooks") },
            )
        }
    }
}
