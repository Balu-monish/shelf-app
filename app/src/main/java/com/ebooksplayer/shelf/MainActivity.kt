package com.ebooksplayer.shelf

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.ebooksplayer.shelf.data.settings.AppSettingsRepository
import com.ebooksplayer.shelf.ui.navigation.ShelfNavHost
import com.ebooksplayer.shelf.ui.theme.ShelfTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A FragmentActivity (not plain ComponentActivity) because the EPUB reader
 * screen hosts Readium's EpubNavigatorFragment, which needs a real
 * FragmentManager to attach to.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: AppSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeMode.collectAsState()
            ShelfTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    ShelfNavHost(navController = navController)
                }
            }
        }
    }
}
