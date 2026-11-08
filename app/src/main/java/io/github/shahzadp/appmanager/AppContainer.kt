package io.github.shahzadp.appmanager

import android.content.Context
import io.github.shahzadp.appmanager.data.AppPrefs
import io.github.shahzadp.appmanager.data.CatalogRepository
import io.github.shahzadp.appmanager.data.IconRepository
import io.github.shahzadp.appmanager.data.InstalledApps
import io.github.shahzadp.appmanager.data.ReleaseRepository
import io.github.shahzadp.appmanager.install.InstallController

/** One object graph, created once from the application. */
class AppContainer(context: Context) {
    val prefs = AppPrefs(context)
    val catalog = CatalogRepository(context)
    val installed = InstalledApps(context)
    val releases = ReleaseRepository(context)
    val icons = IconRepository(context)
    val installs = InstallController(context, installed, icons)
}
