package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import android.content.Context
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.googledriveextractor.GoogleDriveExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Anichin : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Anichin"
    override val baseUrl = "https://anichin.cafe"
    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", Context.MODE_PRIVATE)
    }

    private val okruExtractor by lazy { OkruExtractor(client) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val googleDriveExtractor by lazy { GoogleDriveExtractor(client, headers) }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/ongoing/?page=$page")
    }

    override fun popularAnimeSelector(): String = "div.listupd article.bs"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            // safe extraction to avoid NPE if structure slightly changes
            element.selectFirst("div.bsx > a")?.attr("href")?.let { setUrlWithoutDomain(it) }
            element.selectFirst("div.bsx img")?.let { img ->
                val dataSrc = img.attr("data-src").ifEmpty { img.attr("src") }
                thumbnail_url = dataSrc.ifEmpty { null }
            }
            title = element.selectFirst("div.bsx a")?.attr("title")
                ?: element.selectFirst("div.bsx a")?.text()
                ?: ""
        }
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a.next"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        // Use ongoing page for latest because homepage shows episodes, not anime
        return GET("$baseUrl/ongoing/?page=$page")
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = AnichinFilters.getSearchParameters(filters)

        return when {
            query.isNotEmpty() -> GET("$baseUrl/?s=$query&page=$page")
            params.genre.isNotEmpty() -> GET("$baseUrl/genres/${params.genre}/?page=$page")
            else -> popularAnimeRequest(page)
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1.entry-title")?.text() ?: ""
            document.selectFirst("div.thumb img")?.let { img ->
                val dataSrc = img.attr("data-src").ifEmpty { img.attr("src") }
                thumbnail_url = dataSrc.ifEmpty { null }
            }

            genre = document.select("div.genxed a").joinToString(", ") { it.text() }

            val statusText = document.select("div.status").text()
            status = when {
                statusText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
                statusText.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }

            description = buildString {
                document.selectFirst("div.desc")?.text()?.let { append(it) }
            }
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector(): String = "div.eplister ul li"

    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            element.selectFirst("a")?.attr("href")?.let { setUrlWithoutDomain(it) }
            name = element.selectFirst("span.epcur")?.text()
                ?: element.selectFirst("a")?.text()
                ?: "Episode"

            // Extract first occurrence of integer or decimal (e.g., 12 or 12.5)
            val match = Regex("""\d+(\.\d+)?""").find(name)
            episode_number = match?.value?.toFloatOrNull() ?: 0f

            date_upload = System.currentTimeMillis()
        }
    }

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        // Check select dropdown for servers - PRIORITY
        document.select("select.mirror option").forEach { option ->
            val serverValue = option.attr("value")
            val serverName = option.text().trim()
            val dataIndex = option.attr("data-index")

            if (serverValue.isNotEmpty() && !serverName.contains("Select", ignoreCase = true)) {
                try {
                    // Decode base64-encoded server URL if possible; fallback to raw value
                    val decodedUrl = try {
                        val decoded = String(android.util.Base64.decode(serverValue, android.util.Base64.DEFAULT))
                        // only accept decoded if it looks like an URL
                        if (decoded.startsWith("http", ignoreCase = true) || decoded.startsWith("//")) decoded else serverValue
                    } catch (e: Exception) {
                        serverValue // If not base64, use as-is
                    }

                    val cleanUrl = when {
                        decodedUrl.startsWith("//") -> "https:$decodedUrl"
                        decodedUrl.startsWith("http", ignoreCase = true) -> decodedUrl
                        else -> serverValue
                    }

                    android.util.Log.d("Anichin", "Server: $serverName | URL: $cleanUrl")

                    extractVideoFromUrl(cleanUrl, videoList, if (dataIndex.isNotEmpty()) "$serverName #$dataIndex" else serverName)
                } catch (e: Exception) {
                    android.util.Log.e("Anichin", "Failed to parse server $serverName: ${e.message}")
                }
            }
        }

        // Fallback: check for direct iframe in player-embed (secondary)
        if (videoList.isEmpty()) {
            document.selectFirst("div.player-embed iframe, div#pembed iframe")?.attr("src")?.let { iframeUrl ->
                if (iframeUrl.isNotEmpty()) {
                    val cleanUrl = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
                    extractVideoFromUrl(cleanUrl, videoList, "Main Player")
                }
            }
        }

        // Last resort: try to find any iframe
        if (videoList.isEmpty()) {
            document.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotEmpty() && (src.startsWith("http", ignoreCase = true) || src.startsWith("//"))) {
                    val cleanSrc = if (src.startsWith("//")) "https:$src" else src
                    extractVideoFromUrl(cleanSrc, videoList, "Iframe Fallback")
                }
            }
        }

        return videoList.ifEmpty {
            // Ultimate fallback: return WebView option
            listOf(
                Video(
                    response.request.url.toString(),
                    "Open in WebView (Tap to Play)",
                    response.request.url.toString(),
                ),
            )
        }
    }

    private fun extractVideoFromUrl(url: String, videoList: MutableList<Video>, serverName: String) {
        try {
            // Debug log
            android.util.Log.d("Anichin", "Extracting from URL: $url (Server: $serverName)")

            when {
                url.contains("ok.ru", ignoreCase = true) || url.contains("odnoklassniki", ignoreCase = true) -> {
                    val videos = okruExtractor.videosFromUrl(url, "$serverName - ")
                    videos.forEach { video ->
                        android.util.Log.d("Anichin", "OK.ru Video: ${video.quality} -> ${video.url}")
                    }
                    videoList.addAll(videos)
                }
                url.contains("dailymotion", ignoreCase = true) -> {
                    val videos = dailymotionExtractor.videosFromUrl(url, prefix = "$serverName - ")
                    videos.forEach { video ->
                        android.util.Log.d("Anichin", "Dailymotion Video: ${video.quality} -> ${video.url}")
                    }
                    videoList.addAll(videos)
                }
                url.contains("drive.google", ignoreCase = true) || url.contains("drive.usercontent.google", ignoreCase = true) -> {
                    val videos = googleDriveExtractor.videosFromUrl(url, "$serverName - ")
                    videos.forEach { video ->
                        android.util.Log.d("Anichin", "GDrive Video: ${video.quality} -> ${video.url}")
                    }
                    videoList.addAll(videos)
                }
                url.contains("rumble", ignoreCase = true) -> {
                    android.util.Log.d("Anichin", "Rumble (iframe): $url")
                    videoList.add(Video(url, "$serverName (Rumble)", url))
                }
                else -> {
                    android.util.Log.d("Anichin", "Generic server: $url")
                    videoList.add(Video(url, serverName, url))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Anichin", "Extraction failed for $url: ${e.message}")
            videoList.add(Video(url, "$serverName (Fallback)", url))
        }
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("NOTE: Filters are ignored if using text search!"),
            AnimeFilter.Separator(),
            GenreFilter(),
        )
    }

    private class GenreFilter : UriPartFilter(
        "Genres",
        arrayOf(
            Pair("<select>", ""),
            Pair("Action", "action"),
            Pair("Action Drama", "action-drama"),
            Pair("Actions", "actions"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Historical", "historical"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Romance", "romance"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Supernatural", "supernatural"),
            Pair("Thriller", "thriller"),
            Pair("Xianxia", "xianxia"),
            Pair("Xuanhuan", "xuanhuan"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"

            // Load saved value so UI shows current selection
            val saved = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)
            value = saved

            setOnPreferenceChangeListener { pref, newValue ->
                val selected = newValue as String
                // store the selection
                preferences.edit().putString(key, selected).apply()
                // update the preference UI
                pref.summary = selected
                true
            }
        }.also(screen::addPreference)
    }

    // ============================= Utilities ==============================

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
        private val PREF_QUALITY_VALUES = arrayOf("1080p", "720p", "480p", "360p")
    }
}
