package io.github.shahzadp.appmanager.data

import android.content.Context
import android.content.pm.PackageManager

data class InstalledApp(
    val versionName: String?,
    val versionCode: Long,
)

/** Reads the tiny slice of PackageManager the manager needs. */
class InstalledApps(private val context: Context) {

    fun snapshot(items: List<CatalogItem>): Map<String, InstalledApp> = buildMap {
        for (item in items) {
            val pkg = item.packageName ?: continue
            info(pkg)?.let { put(pkg, it) }
        }
    }

    fun info(packageName: String): InstalledApp? = try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        InstalledApp(
            versionName = info.versionName,
            versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            },
        )
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}
