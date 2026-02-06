package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Extractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Lk21Movies : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "LK21 Movies"
    override val baseUrl by lazy { fetchBaseUrl() }
    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val extractor by lazy { Lk21Extractor(client, headers) }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/page/$page/", headers)
    }

    // Filter hanya movies (exclude yang punya span.episode)
    override fun popularAnimeSelector() = "article.item:not(:has(span.episode))"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            thumbnail_url = element.selectFirst("img")?.attr("src")
            title = element.selectFirst("h2")?.text() ?: ""
        }
    }

    override fun popularAnimeNextPageSelector() = "div.pagination a.next"

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page/", headers)
    }

    // Filter hanya movies (exclude yang punya span.episode)
    override fun latestUpdatesSelector() = "article.item:not(:has(span.episode))"

    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // ============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val genreFilter = filterList.find { it is GenreFilter } as? GenreFilter
        val yearFilter = filterList.find { it is YearFilter } as? YearFilter
        val countryFilter = filterList.find { it is CountryFilter } as? CountryFilter

        return when {
            query.isNotBlank() -> GET("$baseUrl/page/$page/?s=$query", headers)
            genreFilter != null && genreFilter.state != 0 -> {
                val genre = genreFilter.toUriPart()
                GET("$baseUrl/genre/$genre/page/$page/", headers)
            }
            yearFilter != null && yearFilter.state != 0 -> {
                val year = yearFilter.toUriPart()
                GET("$baseUrl/year/$year/page/$page/", headers)
            }
            countryFilter != null && countryFilter.state != 0 -> {
                val country = countryFilter.toUriPart()
                GET("$baseUrl/country/$country/page/$page/", headers)
            }
            else -> popularAnimeRequest(page)
        }
    }

    // Filter hanya movies (exclude yang punya span.episode)
    override fun searchAnimeSelector() = "article.item:not(:has(span.episode))"

    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // ============================== Details ===============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1.entry-title")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.poster img")?.attr("src")

            val infoDiv = document.selectFirst("div.info")
            genre = infoDiv?.select("a[rel=tag]")?.joinToString { it.text() }
            status = SAnime.COMPLETED // Movies are always completed

            description = buildString {
                document.selectFirst("div.entry-content p")?.text()?.let {
                    append(it)
                    append("\n\n")
                }

                infoDiv?.select("p")?.forEach { p ->
                    val text = p.text()
                    if (text.isNotBlank()) {
                        append(text)
                        append("\n")
                    }
                }
            }
        }
    }

    // ============================== Episodes ===============================

    override fun episodeListSelector() = "div.player-area" // Dummy selector

    override fun episodeListParse(response: Response): List<SEpisode> {
        // Movies hanya punya 1 episode
        return listOf(
            SEpisode.create().apply {
                name = "Movie"
                episode_number = 1F
                setUrlWithoutDomain(response.request.url.toString())
            },
        )
    }

    override fun episodeFromElement(element: Element) = throw UnsupportedOperationException()

    // ============================== Videos ===============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        // Scrape dari #player-list > li > a
        document.select("#player-list > li > a[data-url]").forEach { link ->
            val iframeUrl = link.attr("data-url")
            val serverName = link.attr("data-server").uppercase()

            if (iframeUrl.isNotBlank()) {
                try {
                    // Extract videos menggunakan Lk21Extractor
                    val videos = extractor.videosFromUrl(iframeUrl, serverName)
                    videoList.addAll(videos)
                } catch (e: Exception) {
                    // Skip jika gagal extract
                }
            }
        }

        // Fallback: coba dari dropdown select jika list kosong
        if (videoList.isEmpty()) {
            document.select("#player-select > option[value]").forEach { option ->
                val iframeUrl = option.attr("value")
                val serverName = option.attr("data-server").uppercase()

                if (iframeUrl.isNotBlank()) {
                    try {
                        val videos = extractor.videosFromUrl(iframeUrl, serverName)
                        videoList.addAll(videos)
                    } catch (e: Exception) {
                        // Skip jika gagal extract
                    }
                }
            }
        }

        return videoList
    }

    override fun videoListSelector() = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList() = LK21Filters.FILTER_LIST

    // ============================== Settings ===============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Lk21Preferences.addPreferencesToScreen(screen, preferences, baseUrl)
    }

    // ============================== Utilities ===============================

    /**
     * Fetch base URL dari d21.team
     * Fallback ke default jika gagal
     */
    private fun fetchBaseUrl(): String {
        return try {
            val request = GET("https://d21.team/", headers)
            val response = client.newCall(request).execute()
            val document = response.asJsoup()

            // Cari link dengan class "link-logo" atau href yang mengandung "lk21"
            val link = document.selectFirst("a.link-logo")?.attr("href")
                ?: document.select("a[href*=lk21]").firstOrNull()?.attr("href")
                ?: "https://tv8.lk21official.cc/"

            // Pastikan URL diakhiri dengan "/"
            if (link.endsWith("/")) link else "$link/"
        } catch (e: Exception) {
            // Fallback ke default URL
            "https://tv8.lk21official.cc/"
        }
    }

    companion object {
        private const val PREF_DOMAIN_KEY = "preferred_domain"
        private const val PREF_SERVER_KEY = "preferred_server"
    }
}
