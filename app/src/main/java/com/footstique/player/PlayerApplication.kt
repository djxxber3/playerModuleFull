package com.footstique.player
import android.app.Application
import timber.log.Timber
class PlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
            Timber.plant(Timber.DebugTree())
    }
}