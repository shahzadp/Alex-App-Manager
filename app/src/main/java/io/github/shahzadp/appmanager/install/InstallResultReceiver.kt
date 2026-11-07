package io.github.shahzadp.appmanager.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.shahzadp.appmanager.AlexApps

/**
 * Result callback for a PackageInstaller session. Manifest-registered so it
 * still fires after the activity has been recreated while the system installer
 * is in front.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AlexApps.container?.installs?.onSessionResult(intent)
    }

    companion object {
        const val EXTRA_APP_ID = "io.github.shahzadp.appmanager.APP_ID"
        const val EXTRA_PACKAGE = "io.github.shahzadp.appmanager.PACKAGE"
    }
}
