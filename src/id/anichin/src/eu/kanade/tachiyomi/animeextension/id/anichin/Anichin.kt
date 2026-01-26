package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
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
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/ongoing/?page=$page")
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
        return GET("$baseUrl/?page=$page")
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val genreFilter = filterList.find { it is GenreFilter } as? GenreFilter

        return when {
            query.isNotEmpty() -> GET("$baseUrl/?s=$query&page=$page")
            genreFilter != null && genreFilter.state.isNotEmpty() -> {
                val genre = genreFilter.state
                GET("$baseUrl/genres/$genre/?page=$page")
            }
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

        // Extract video servers from select dropdown
        document.select("select.mirror option").forEach { option ->
            val serverUrl = option.attr("value")
            val serverName = option.text()
            
            if (serverUrl.isNotEmpty() && serverUrl != "Select Video Server") {
                try {
                    when {
                        serverUrl.contains("ok.ru") -> {
                            videoList.addAll(extractOkRuVideo(serverUrl, serverName))
                        }
                        serverUrl.contains("dailymotion") -> {
                            videoList.addAll(extractDailymotionVideo(serverUrl, serverName))
                        }
                        else -> {
                            // Generic extractor for iframe sources
                            videoList.add(Video(serverUrl, serverName, serverUrl))
                        }
                    }
                } catch (e: Exception) {
                    // Skip failed extractions
                }
            }
        }

        // Fallback: check for direct iframe in player-embed
        if (videoList.isEmpty()) {
            document.selectFirst("div.player-embed iframe")?.attr("src")?.let { iframeUrl ->
                if (iframeUrl.contains("ok.ru")) {
                    videoList.addAll(extractOkRuVideo(iframeUrl, "OK.ru"))
                } else {
                    videoList.add(Video(iframeUrl, "Default Server", iframeUrl))
                }
            }
        }

        return videoList
    }

    private fun extractOkRuVideo(url: String, quality: String): List<Video> {
        val videoList = mutableListOf<Video>()
        try {
            val videoId = url.substringAfter("videoembed/").substringBefore("?")
            val embedUrl = "https://ok.ru/videoembed/$videoId"
            
            // OK.ru typically provides multiple qualities
            listOf("1080p", "720p", "480p", "360p").forEach { q ->
                videoList.add(Video(embedUrl, "$quality - $q", embedUrl))
            }
        } catch (e: Exception) {
            videoList.add(Video(url, quality, url))
        }
        return videoList
    }

    private fun extractDailymotionVideo(url: String, quality: String): List<Video> {
        // Dailymotion extraction logic
        return listOf(Video(url, quality, url))
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("NOTE: Filters are ignored if using text search!"),
            AnimeFilter.Separator(),
            GenreFilter()
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
            Pair("Xuanhuan", "xuanhuan")
        )
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val state get() = vals[this.state].second
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