package com.ebooksplayer.shelf

import android.app.Application
import com.ebooksplayer.shelf.data.samples.SampleContentSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ShelfApplication : Application() {

    @Inject lateinit var sampleContentSeeder: SampleContentSeeder

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { sampleContentSeeder.seedIfNeeded() }
    }
}
