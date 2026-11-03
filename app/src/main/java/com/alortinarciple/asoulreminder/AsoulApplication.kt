package com.alortinarciple.asoulreminder

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.alortinarciple.asoulreminder.background.BackgroundScheduler
import com.alortinarciple.asoulreminder.data.AppPreferences
import com.alortinarciple.asoulreminder.data.NetworkClient
import com.alortinarciple.asoulreminder.data.ReminderRepository
import com.alortinarciple.asoulreminder.notifications.NotificationHelper

class AsoulApplication : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.createChannels()
        val settings = container.repository.state.value.settings
        BackgroundScheduler.schedulePeriodic(this, settings)
        BackgroundScheduler.ensureMonitor(this, settings)
        BackgroundScheduler.scheduleLiveWindows(
            this,
            container.repository.state.value.cache.events,
            settings,
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}

class AppContainer(application: Application) {
    val preferences = AppPreferences(application).apply { importBundledConfigIfPresent() }
    val notifications = NotificationHelper(application, preferences)
    val repository = ReminderRepository(
        context = application,
        preferences = preferences,
        network = NetworkClient(),
        notifications = notifications,
    )
}
