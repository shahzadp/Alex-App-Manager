package io.github.shahzadp.appmanager.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps the launcher icons for the catalogue up to date. Bundled icons are the
 * seed until a refresh notices that the release on GitHub moved on; at that
 * point the release APK is downloaded once, the icon is extracted with
 * [PackageManager.getPackageArchiveInfo] and cached inside app storage, and
 * every screen picks the new artwork up.
 */
class IconRepository(private val context: Context) {

    private val dir = File(context.filesDir, "icons").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("icon-cache", Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    /** Bumped whenever any cached icon is replaced; UI uses it to recompose. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun current(id: String): File = File(dir, "$id.png")

    suspend fun maybeRefresh(items: List<CatalogItem>, releases: Map<String, Release>) {
        withContext(Dispatchers.IO) {
            for (item in items) {
                if (item.isWeb) continue
                val release = releases[item.id] ?: continue
                val cachedVersion = prefs.getString(versionKey(item.id), null)
                if (cachedVersion == null) {
                    // Seed from the pinned/current tag so a fresh install never
                    // downloads anything until GitHub ships a new release.
                    prefs.edit().putString(versionKey(item.id), release.tag).apply()
                    continue
                }
                if (cachedVersion == release.tag) continue
                fetch(item, release)
            }
        }
    }

    /** Downloads the release APK and caches its launcher icon. Best effort. */
    suspend fun fetch(item: CatalogItem, release: Release): Boolean = withContext(Dispatchers.IO) {
        val asset = release.apk(item) ?: return@withContext false
        val temp = File(context.cacheDir, "icon-fetch-${item.id}.apk")
        try {
            download(asset.url, temp, asset.size)
            extract(item, release, temp)
        } catch (_: Exception) {
            false
        } finally {
            temp.delete()
        }
    }

    /** Extracts the launcher icon from an APK we already have on disk. */
    suspend fun extract(item: CatalogItem, release: Release, apkFile: File): Boolean =
        withContext(Dispatchers.IO) { extractAndStore(item, release.tag, apkFile) }

    private fun extractAndStore(item: CatalogItem, version: String, apkFile: File): Boolean =
        try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_META_DATA)
                ?: return false
            val appInfo = info.applicationInfo ?: return false
            val drawable = appInfo.loadIcon(pm) ?: return false
            val side = 512
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, side, side)
            drawable.draw(canvas)
            val out = File(dir, "${item.id}.png.tmp")
            out.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            val target = current(item.id)
            if (out.renameTo(target)) {
                prefs.edit().putString(versionKey(item.id), version).apply()
                _revision.value = _revision.value + 1
                true
            } else {
                out.delete()
                false
            }
        } catch (_: Exception) {
            false
        }

    private fun download(url: String, destination: File, expectedSize: Long) {
        destination.parentFile?.mkdirs()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 45_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "AlexApps/0.2.1 (+https://github.com/shahzadp)")
            }
            if (connection.responseCode !in 200..299) {
                error("Download failed (HTTP ${connection.responseCode}).")
            }
            connection.inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            if (expectedSize > 0 && destination.length() != expectedSize) {
                error("The download stopped early.")
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun versionKey(id: String) = "version:$id"
}



