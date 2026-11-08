package io.github.shahzadp.appmanager.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
                    // Fresh install or cleared data: bundled icons stay until the
                    // next release tag moves. Don't download anything here.
                    prefs.edit().putString(versionKey(item.id), release.tag).apply()
                    continue
                }
                if (cachedVersion == release.tag) continue
                // A new release tag — replace cached icons from the new APK.
                fetch(item, release)
            }
        }
    }

    /**
     * Cheapest path first: a tiny icon asset published alongside the APK
     * ("ic_launcher_icon.png", a few KB). Falls back to extracting from the
     * release APK when the publishers didn't ship the asset.
     */
    suspend fun fetch(item: CatalogItem, release: Release): Boolean = withContext(Dispatchers.IO) {
        fetchFromSmallAsset(item, release) || fetchFromFullApk(item, release)
    }

    /** Tries small icon assets published for the release tag. */
    private suspend fun fetchFromSmallAsset(item: CatalogItem, release: Release): Boolean {
        val temp = File(context.cacheDir, "icon-fetch-${item.id}.asset")
        for (name in ICON_ASSET_NAMES) {
            val url = canonicalDownloadUrl(item.repo, release.tag, name) ?: continue
            try {
                download(url, temp, 0L)
            } catch (_: Exception) {
                runCatching { temp.delete() }
                continue
            }
            val bytes = runCatching { temp.readBytes() }.getOrNull()
            temp.delete()
            if (bytes == null) continue
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            val side = 512
            val square = Bitmap.createScaledBitmap(decoded, side, side, true)
            decoded.recycle()
            if (saveIcon(item, release.tag, square)) {
                return true
            }
        }
        return false
    }

    private suspend fun fetchFromFullApk(item: CatalogItem, release: Release): Boolean =
        withContext(Dispatchers.IO) {
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
            drawable.setBounds(0, 0, side, side)
            drawable.draw(Canvas(bitmap))
            saveIcon(item, version, bitmap)
        } catch (_: Exception) {
            false
        }

    private fun saveIcon(item: CatalogItem, version: String, bitmap: Bitmap): Boolean =
        try {
            val out = File(dir, "${item.id}.png.tmp")
            out.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            bitmap.recycle()
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
                setRequestProperty("User-Agent", "AlexApps/0.2.6 (+https://github.com/shahzadp)")
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

    private companion object {
        /** Small icon assets, in priority order. First match wins; honest names. */
        private val ICON_ASSET_NAMES = listOf(
            "ic_launcher_icon.png",
            "ic_launcher_icon.webp",
            "icon-512.png",
            "icon.png",
            "ic_launcher.png",
        )
    }
}









