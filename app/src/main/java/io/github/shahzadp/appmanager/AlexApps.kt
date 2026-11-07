package io.github.shahzadp.appmanager

import android.app.Application

class AlexApps : Application() {

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        /**
         * Nullable only for the instant before [onCreate] runs; the manifest
         * receiver and the activity both tolerate that.
         */
        @Volatile
        var container: AppContainer? = null
            private set
    }
}
