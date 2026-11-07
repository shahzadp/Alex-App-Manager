package io.github.shahzadp.appmanager.data

import android.content.Context
import android.graphics.Color
import androidx.annotation.DrawableRes
import io.github.shahzadp.appmanager.R
import org.json.JSONArray
import org.json.JSONObject

enum class Section(val label: String) {
    APP("Apps"),
    GAME("Games"),
    WEB("Web"),
}

/**
 * One entry in the pre-configured catalogue. Everything the manager needs to
 * show an app, find its release on GitHub and verify the downloaded APK.
 */
data class CatalogItem(
    val id: String,
    val name: String,
    val tagline: String,
    val description: String,
    val section: Section,
    val repo: String?,
    val packageName: String?,
    val assetPattern: Regex?,
    val pinnedVersion: String,
    val pinnedAsset: String?,
    val pinnedSize: Long,
    val webUrl: String?,
    val caveat: String?,
    @param:DrawableRes val iconRes: Int,
    val accent: Long,
    val tags: List<String>,
    val minAndroid: String?,
) {
    val isWeb: Boolean get() = section == Section.WEB
    val owner: String? get() = repo?.substringBefore('/')
    val repoName: String? get() = repo?.substringAfter('/', "")
    val repoUrl: String? get() = repo?.let { "https://github.com/$it" }
}

data class Catalog(
    val profileUrl: String,
    val items: List<CatalogItem>,
)

/**
 * Loads the bundled catalogue. It is deliberately a file in assets rather than
 * a network fetch: the list of apps is fixed and must work on a fresh install
 * with no connection.
 */
class CatalogRepository(context: Context) {

    val catalog: Catalog = parse(context)

    private fun parse(context: Context): Catalog {
        val raw = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val array = root.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                add(
                    CatalogItem(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        tagline = json.getString("tagline"),
                        description = json.optString("description").orEmpty(),
                        section = when (json.optString("section")) {
                            "GAME" -> Section.GAME
                            "WEB" -> Section.WEB
                            else -> Section.APP
                        },
                        repo = json.optStringOrNull("repo"),
                        packageName = json.optStringOrNull("package"),
                        assetPattern = json.optStringOrNull("assetPattern")?.let { Regex(it) },
                        pinnedVersion = json.optString("version").orEmpty(),
                        pinnedAsset = json.optStringOrNull("asset"),
                        pinnedSize = json.optLong("size", 0L),
                        webUrl = json.optStringOrNull("webUrl"),
                        caveat = json.optStringOrNull("caveat"),
                        iconRes = iconResFor(json.optString("icon")),
                        accent = parseColor(json.optString("accent")),
                        tags = json.optStringArray("tags"),
                        minAndroid = json.optStringOrNull("minAndroid"),
                    ),
                )
            }
        }
        return Catalog(
            profileUrl = root.optString("profileUrl", "https://github.com/shahzadp"),
            items = items,
        )
    }

    private fun iconResFor(name: String): Int = when (name) {
        "ic_app_recorder" -> R.drawable.ic_app_recorder
        "ic_app_photoeditor" -> R.drawable.ic_app_photoeditor
        "ic_app_games" -> R.drawable.ic_app_games
        "ic_app_alice" -> R.drawable.ic_app_alice
        "ic_app_dol" -> R.drawable.ic_app_dol
        "ic_app_store" -> R.drawable.ic_app_store
        "ic_app_portfolio" -> R.drawable.ic_app_portfolio
        else -> R.drawable.ic_launcher_foreground
    }
}

private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() && it != "null" }
}

private fun JSONObject.optStringArray(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) add(array.optString(index))
    }
}

private fun parseColor(value: String): Long {
    if (value.isBlank()) return 0xFF3D33C9
    return try {
        val color = Color.parseColor(value)
        color.toLong() and 0xFFFFFFFFL
    } catch (_: IllegalArgumentException) {
        0xFF3D33C9
    }
}
