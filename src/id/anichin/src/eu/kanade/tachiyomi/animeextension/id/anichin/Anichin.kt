package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.cloudflareinterceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.googledriveextractor.GoogleDriveExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class Anichin : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Anichin"
    override val baseUrl = "https://anichin.watch"
    override val lang = "id"
    override val supportsLatest = true

    // FIX 1: Gunakan CloudflareInterceptor dengan client biasa, bukan cloudflareClient
    private val cloudflareInterceptor by lazy { 
        CloudflareInterceptor(network.client) 
    }

    // FIX 2: Timeout diperbesar dan hapus duplikasi interceptor
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(cloudflareInterceptor)
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    // FIX 3: Tambahkan headers yang proper untuk bypass Cloudflare
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        add("Referer", baseUrl)
        add("DNT", "1")
        add("Connection", "keep-alive")
        add("Upgrade-Insecure-Requests", "1")
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val okruExtractor by lazy { OkruExtractor(client) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val googleDriveExtractor by lazy { GoogleDriveExtractor(client, headers) }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/ongoing/?page=$page", headers)
    }

    override fun popularAnimeSelector(): String = "div.listupd article.bs"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            setUrlWithoutDomain(element.selectFirst("div.bsx > a")!!.attr("href"))
            thumbnail_url = element.selectFirst("div.bsx img")?.attr("src")
            title = element.selectFirst("div.bsx a")?.attr("title") ?: ""
        }
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a.next"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/ongoing/?page=$page", headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = AnichinFilters.getSearchParameters(filters)

        val url = when {
            query.isNotEmpty() -> "$baseUrl/?s=$query&page=$page"
            params.genre.isNotEmpty() -> "$baseUrl/genres/${params.genre}/?page=$page"
            else -> "$baseUrl/ongoing/?page=$page"
        }

        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1.entry-title")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.thumb img")?.attr("src")

            genre = document.select("div.genxed a").joinToString { it.text() }

            status = when {
                document.select("div.status").text().contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
                document.select("div.status").text().contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
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
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            name = element.selectFirst("span.epcur")?.text() ?: element.selectFirst("a")?.text() ?: "Episode"
            episode_number = name.filter { it.isDigit() }.toFloatOrNull() ?: 0f
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
                    // Decode base64-encoded server URL
                    val decodedUrl = try {
                        String(android.util.Base64.decode(serverValue, android.util.Base64.DEFAULT))
                    } catch (e: Exception) {
                        serverValue // If not base64, use as-is
                    }

                    val cleanUrl = when {
                        decodedUrl.startsWith("//") -> "https:$decodedUrl"
                        decodedUrl.startsWith("http") -> decodedUrl
                        else -> serverValue
                    }

                    android.util.Log.d("Anichin", "Server: $serverName | URL: $cleanUrl")

                    extractVideoFromUrl(cleanUrl, videoList, "$serverName #$dataIndex")
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
                if (src.isNotEmpty() && (src.startsWith("http") || src.startsWith("//"))) {
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
                url.contains("ok.ru") || url.contains("odnoklassniki") -> {
                    val videos = okruExtractor.videosFromUrl(url, "$serverName - ")
                    videos.forEach { video ->
                        android.util.Log.d("Anichin", "OK.ru Video: ${video.quality} -> ${video.url}")
                    }
                    videoList.addAll(videos)
                }
                url.contains("dailymotion") -> {
                    val videos = dailymotionExtractor.videosFromUrl(url, prefix = "$serverName - ")
                    videos.forEach { video ->
                        android.util.Log.d("Anichin", "Dailymotion Video: ${video.quality} -> ${video.url}")
                    }
                    videoList.addAll(videos)
                }
                url.contains("drive.google") || url.contains("drive.usercontent.google") -> {
                    val videos = googleDriveExtractor.videosFromUrl(url, "$serverName - ")
                    videos.forEach { video ->
                        android.util.Log.d("Anichin", "GDrive Video: ${video.quality} -> ${video.url}")
                    }
                    videoList.addAll(videos)
                }
                url.contains("rumble") -> {
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

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
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

// ============================== Filter Data ===============================

data class AnichinFiltersData(val genre: String)

object AnichinFilters {
    fun getSearchParameters(filters: AnimeFilterList): AnichinFiltersData {
        var genre = ""

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genre = filter.toUriPart()
                else -> {}
            }
        }

        return AnichinFiltersData(genre)
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
}
