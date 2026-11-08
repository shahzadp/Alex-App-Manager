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

    /**
     * Probes GitHub for a new release. The `releases.atom` feed carries the
     * same information but doesn't count against the unauthenticated REST
     * budget, so routine checks cost nothing even when the API limit is spent.
     * Only a tag change (or feed trouble) triggers a single `releases/latest`
     * REST call per changed app; when even that is unavailable, the cached
     * catalogue stays authoritative.
     */
    suspend fun fetch(item: CatalogItem): FetchOutcome = withContext(Dispatchers.IO) {
        val repo = item.repo ?: return@withContext FetchOutcome.Failed("This entry is not hosted on GitHub.")

        val probe = runCatching { probeRss(item) }.getOrNull()
        if (probe != null) {
            val cachedNow = cached(item)
            prefs.edit().putLong(lastFetchKey(item.id), System.currentTimeMillis()).apply()
            val asset = probe.assets.firstOrNull()
            val headSize = asset?.url?.let { headAsset(it) }
            return@withContext when {
                // Same version: re-derive URL and true size even when the tag
                // didn't move; this heals a catalogue/build mismatch without the API.
                cachedNow != null && cachedNow.tag == probe.tag -> {
                    val cachedAsset = cachedNow.apk(item)
                    if (asset != null && headSize != null && headSize > 0 &&
                        (cachedAsset == null ||
                            cachedAsset.size != headSize ||
                            cachedAsset.url != asset.url)
                    ) {
                        val fixed = probe.copy(assets = listOf(asset.copy(size = headSize)))
                        store(item, fixed, null)
                        FetchOutcome.Updated(fixed)
                    } else {
                        FetchOutcome.Unchanged(cachedNow)
                    }
                }

                // New version: trust HEAD to establish the real asset before
                // advertising it; if HEAD can't decide, let the API answer.
                headSize != null && asset != null -> {
                    val size = if (headSize > 0) headSize else 0L // 0 = size unknown
                    val fixed = probe.copy(assets = listOf(asset.copy(size = size)))
                    store(item, fixed, null)
                    FetchOutcome.Updated(fixed)
                }

                else -> fetchViaApi(item)
            }
        }

        // RSS unavailable or unparseable: fall back to one REST call per app.
        fetchViaApi(item)
    }

    /** Authoritative version check via the REST API (used only when RSS can't do it). */
    private suspend fun fetchViaApi(item: CatalogItem): FetchOutcome = withContext(Dispatchers.IO) {
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
                setRequestProperty("User-Agent", "AlexApps/0.2.5 (+https://github.com/shahzadp)")
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

    /** Parses the public `releases.atom` feed; returns null on any trouble. */
    private fun probeRss(item: CatalogItem): Release? {
        val repo = item.repo ?: return null
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("https://github.com/$repo/releases.atom").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/atom+xml")
                // Never send If-None-Match: the feed keeps the same weak ETag
                // across content changes, so the app could get stuck on 304
                // forever and never see a new release. The payload is ~5 KB
                // and isn't billed against the API budget, so always fetch it.
                setRequestProperty("User-Agent", "AlexApps/0.2.5 (+https://github.com/shahzadp)")
            }

            val code = connection.responseCode
            if (code !in 200..299) return null

            val body = connection.inputStream.bufferedReader().use { it.readText() }

            val entry = Regex("(?s)<entry>.*?</entry>").find(body)?.value ?: return null
            val tag = Regex("releases/tag/([^\"'<]+)").find(entry)?.groupValues?.get(1)?.let { urlDecode(it) }
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val title = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
                .find(entry)?.groupValues?.get(1)?.let(::stripHtml)?.trim()
                ?.ifBlank { tag } ?: tag
            val updated = Regex("<updated>(.*?)</updated>").find(entry)?.groupValues?.get(1)
            val notes = Regex("(?s)<content[^>]*>(.*?)</content>", RegexOption.IGNORE_CASE)
                .find(entry)?.groupValues?.get(1)
                ?: Regex("(?s)<summary[^>]*>(.*?)</summary>", RegexOption.IGNORE_CASE).find(entry)?.groupValues?.get(1)
                ?: ""
            val asset = probeAsset(item, tag)
            return Release(
                tag = tag,
                title = title,
                notes = stripHtml(notes),
                publishedAt = updated,
                assets = listOfNotNull(asset),
                fetchedAt = System.currentTimeMillis(),
                fromNetwork = true,
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun probeAsset(item: CatalogItem, tag: String): ReleaseAsset? {
        val name = guessAssetName(item, tag) ?: return null
        val url = canonicalDownloadUrl(item.repo, tag, name) ?: return null
        return ReleaseAsset(name, item.pinnedSize, url)
    }

    /**
     * Catalogue assets keep stable names, but DoL-style names embed the version
     * ("DoL-by-Alex-0.5.13.6-release.apk"); substitute the new tag so HEAD checks
     * and called downloads stay valid for releases we haven't seen yet.
     */
    private fun guessAssetName(item: CatalogItem, tag: String): String? {
        val base = item.pinnedAsset ?: return null
        val bare = tag.removePrefix("v").removePrefix("V")
        val pinned = item.pinnedVersion
        return if (pinned.isNotBlank() && base.contains(pinned)) {
            base.replace(pinned, bare)
        } else {
            base
        }
    }

    /** True when the (possibly CDN-hosted) asset exists; HEAD with no API budget.
     *  Returns the asset's Content-Length when known, or 0 when the asset exists
     *  but the server doesn't report a size, or null when it doesn't exist. */
    private fun headAsset(url: String): Long? = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 12_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AlexApps/0.2.5 (+https://github.com/shahzadp)")
        }
        val code = connection.responseCode
        val length = if (code in 200..299) connection.contentLengthLong.takeIf { it > 0 } ?: 0L else null
        connection.disconnect()
        length
    } catch (_: Exception) {
        null
    }

    private fun urlDecode(value: String): String = runCatching {
        java.net.URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)

    /** Feed bodies are entity-escaped HTML; decode before stripping tags. */
    private fun stripHtml(raw: String): String {
        if (raw.isBlank()) return ""
        val decoded = decodeEntities(
            raw.replace("<![CDATA[", "").replace("]]>", ""),
        )
        val text = decoded
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<li>", RegexOption.IGNORE_CASE), "- ")
            .replace(Regex("</(p|div|h[1-6]|ul|ol)>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
        return text
            .replace('\u00A0', ' ')
            .replace(Regex("[ \\t]*\n[ \\t]*"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun decodeEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")

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

    /** Parses the stored or network release entry; returns null on trouble. */
    private fun parseRelease(json: JSONObject, item: CatalogItem): Release {
        // Network responses use "tag_name"; our own stored entries use "tag".
        val tag = json.optString("tag_name").ifBlank { json.optString("tag") }
            .ifBlank { "v${item.pinnedVersion}" }
        val assetsJson = json.optJSONArray("assets") ?: JSONArray()
        val assets = buildList {
            for (index in 0 until assetsJson.length()) {
                val asset = assetsJson.optJSONObject(index) ?: continue
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                // Network responses use browser_download_url; our own cache uses url.
                val rawUrl = asset.optString("browser_download_url").ifBlank {
                    asset.optString("url")
                }.takeUnless { apiAssetsEndpoint.matches(it) }.orEmpty()
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
        if (assets.isEmpty() && json.has("assets")) {
            // A stored entry with no usable APK URLs is worse than no entry
            // at all (downloads would fetch the API JSON endpoint instead of
            // the APK). Treat it as absent and let the caller re-fetch.
            throw IllegalStateException("no usable APK assets")
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

/** The REST endpoint for an asset's JSON descriptor — never a browser download URL. */
private val apiAssetsEndpoint =
    Regex("^https?://api\\.github\\.com/repos/[^/]+/[^/]+/releases/assets/\\d+$")





