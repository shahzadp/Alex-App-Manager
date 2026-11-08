package io.github.shahzadp.appmanager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ReleaseAsset(
    val name: String,
    val size: Long,
    val url: String,
)

data class Release(
    val tag: String,
    val title: String,
    val notes: String,
    val publishedAt: String?,
    val assets: List<ReleaseAsset>,
    val fetchedAt: Long,
    val fromNetwork: Boolean,
) {
    val version: String
        get() = tag.removePrefix("v").removePrefix("V").ifBlank { tag }

    /** Picks the APK this entry cares about, honouring the catalogue's pattern. */
    fun apk(item: CatalogItem): ReleaseAsset? {
        val usable = assets.filter { it.isHttpUrl() }
        val pattern = item.assetPattern
        if (pattern != null) {
            usable.firstOrNull { pattern.matches(it.name) }?.let { return it }
        }
        return usable.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
    }
}

private fun ReleaseAsset.isHttpUrl(): Boolean =
    url.startsWith("https://", ignoreCase = true) ||
        url.startsWith("http://", ignoreCase = true)

sealed interface FetchOutcome {
    data class Updated(val release: Release) : FetchOutcome
    data class Unchanged(val release: Release) : FetchOutcome
    data class Failed(val message: String) : FetchOutcome
}

class ReleaseRepository(context: Context) {

    private val prefs = context.getSharedPreferences("release-cache", Context.MODE_PRIVATE)

    /** What ships inside the APK, used before the first successful refresh. */
    fun pinned(item: CatalogItem): Release {
        val assets = item.pinnedAsset?.let { asset ->
            canonicalDownloadUrl(item.repo, "v${item.pinnedVersion}", asset)?.let { url ->
                listOf(
                    ReleaseAsset(
                        name = asset,
                        size = item.pinnedSize,
                        url = url,
                    ),
                )
            }
        }.orEmpty()
        return Release(
            tag = "v${item.pinnedVersion}",
            title = item.name,
            notes = "",
            publishedAt = null,
            assets = assets,
            fetchedAt = 0L,
            fromNetwork = false,
        )
    }

    fun cached(item: CatalogItem): Release? {
        val raw = prefs.getString(key(item.id), null) ?: return null
        return runCatching { parseRelease(JSONObject(raw), item) }.getOrNull()
    }

    fun load(item: CatalogItem): Release = cached(item) ?: pinned(item)

    fun lastFetch(item: CatalogItem): Long =
        prefs.getLong(lastFetchKey(item.id), 0L)

    suspend fun fetch(item: CatalogItem): FetchOutcome = withContext(Dispatchers.IO) {
        val repo = item.repo ?: return@withContext FetchOutcome.Failed("This entry is not hosted on GitHub.")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "AlexApps/0.2 (+https://github.com/shahzadp)")
                prefs.getString(etagKey(item.id), null)?.let { setRequestProperty("If-None-Match", it) }
            }

            when (val code = connection.responseCode) {
                200 -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val release = parseRelease(JSONObject(body), item)
                    store(item, release, connection.getHeaderField("ETag"))
                    FetchOutcome.Updated(release)
                }

                304 -> FetchOutcome.Unchanged(cached(item) ?: pinned(item))

                403, 429 -> {
                    val remaining = connection.getHeaderField("X-RateLimit-Remaining")
                    val message = if (remaining == "0" || code == 429) {
                        "GitHub's unauthenticated limit is used up (60 requests an hour). Cached versions are still available."
                    } else {
                        "GitHub refused the request ($code)."
                    }
                    FetchOutcome.Failed(message)
                }

                404 -> FetchOutcome.Failed("No published release for $repo yet.")

                else -> FetchOutcome.Failed("GitHub answered $code.")
            }
        } catch (throwable: Throwable) {
            FetchOutcome.Failed(throwable.message ?: "Could not reach GitHub.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun store(item: CatalogItem, release: Release, etag: String?) {
        val json = JSONObject().apply {
            put("tag", release.tag)
            put("title", release.title)
            put("notes", release.notes)
            put("publishedAt", release.publishedAt ?: JSONObject.NULL)
            put("fetchedAt", release.fetchedAt)
            put(
                "assets",
                JSONArray().apply {
                    release.assets.forEach { asset ->
                        put(
                            JSONObject().apply {
                                put("name", asset.name)
                                put("size", asset.size)
                                put("url", asset.url)
                            },
                        )
                    }
                },
            )
        }
        prefs.edit()
            .putString(key(item.id), json.toString())
            .putLong(lastFetchKey(item.id), release.fetchedAt)
            .apply {
                if (etag != null) putString(etagKey(item.id), etag)
            }
            .apply()
    }

    private fun parseRelease(json: JSONObject, item: CatalogItem): Release {
        val tag = json.optString("tag_name").ifBlank { "v${item.pinnedVersion}" }
        val assetsJson = json.optJSONArray("assets") ?: JSONArray()
        val assets = buildList {
            for (index in 0 until assetsJson.length()) {
                val asset = assetsJson.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                // Network responses use browser_download_url; our own cache uses url.
                val rawUrl = asset.optString("url").ifBlank {
                    asset.optString("browser_download_url")
                }
                val url = if (rawUrl.startsWith("https://", ignoreCase = true) ||
                    rawUrl.startsWith("http://", ignoreCase = true)
                ) {
                    rawUrl
                } else {
                    canonicalDownloadUrl(item.repo, tag, name) ?: continue
                }
                add(
                    ReleaseAsset(
                        name = name,
                        size = asset.optLong("size", 0L),
                        url = url,
                    ),
                )
            }
        }
        return Release(
            tag = tag,
            title = json.optString("name").ifBlank { item.name },
            notes = json.optString("body").orEmpty(),
            publishedAt = json.optString("published_at").takeIf { it.isNotBlank() && it != "null" },
            assets = assets,
            fetchedAt = System.currentTimeMillis(),
            fromNetwork = true,
        )
    }

    private fun key(id: String) = "release:$id"
    private fun etagKey(id: String) = "etag:$id"
    private fun lastFetchKey(id: String) = "fetched:$id"
}

private fun canonicalDownloadUrl(repo: String?, tag: String, name: String): String? {
    if (repo.isNullOrBlank() || tag.isBlank()) return null
    val encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    return "https://github.com/$repo/releases/download/$tag/$encoded"
}

