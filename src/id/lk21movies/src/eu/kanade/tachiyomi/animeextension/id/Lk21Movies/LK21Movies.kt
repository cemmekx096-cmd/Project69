package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Extractor
import eu.kanade.tachiyomi.lib.lk21extractor.YoutubeExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * LK21Movies Extension v2.0 - Clean Rebuild
 *
 * Features:
 * - Self-healing domain via GitHub JSON
 * - Live filter scraping dengan cache (24h)
 * - YouTube trailer support
 * - Quality selector
 * - Anti-duplicate & drama filter
 */
class LK21Movies : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "LK21Movies"
    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // Base URL dengan self-healing
    override val baseUrl: String
        get() = LK21Config.fetchAndUpdateConfig(client, preferences)

    companion object {
        private const val TAG = "LK21Movies"
    }

    // === INITIALIZATION ===

    init {
        // Initialize filters dengan cache support
        try {
            LK21Filters.initialize(client, baseUrl, preferences)
            Log.d(TAG, "Extension initialized with base URL: $baseUrl")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing extension", e)
        }
    }

    // === PREFERENCE SCREEN ===

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        LK21Preferences.setupPreferenceScreen(screen, preferences)
    }

    // === POPULAR ANIME ===

    override fun popularAnimeSelector() = LK21Parser.POPULAR_SELECTOR

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/populer/page/$page", headers)
    }

    override fun popularAnimeFromElement(element: Element): SAnime {
        return LK21Parser.parseAnimeFromElement(element) ?: SAnime.create()
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()

        val animeList = document.select(popularAnimeSelector())
            .mapNotNull { LK21Parser.parseAnimeFromElement(it) }
            .distinctBy { it.url } // Anti-duplicate

        val hasNextPage = LK21Parser.hasNextPage(document)

        return AnimesPage(animeList, hasNextPage)
    }

    override fun popularAnimeNextPageSelector() = LK21Parser.NEXT_PAGE_SELECTOR

    // === LATEST UPDATES ===

    override fun latestUpdatesSelector() = popularAnimeSelector()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest/page/$page", headers)
    }

    override fun latestUpdatesFromElement(element: Element): SAnime {
        return popularAnimeFromElement(element)
    }

    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // === SEARCH ===

    override fun searchAnimeSelector() = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime {
        return popularAnimeFromElement(element)
    }

    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        // Jika ada query search
        if (query.isNotEmpty()) {
            return GET("$baseUrl/?s=$query&page=$page", headers)
        }

        // Parse filters
        var genreState = 0
        var yearState = 0
        var countryState = 0
        var sortState = 0

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genreState = filter.state
                is YearFilter -> yearState = filter.state
                is CountryFilter -> countryState = filter.state
                is SortFilter -> sortState = filter.state
            }
        }

        val url = LK21Parser.buildFilterUrl(
            baseUrl, page, genreState, yearState, countryState, sortState,
        )

        return GET(url, headers)
    }

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            GenreFilter("Genre"),
            YearFilter(),
            CountryFilter(),
            SortFilter(),
        )
    }

    // === ANIME DETAILS ===

    override fun animeDetailsParse(document: Document): SAnime {
        return LK21Parser.parseAnimeDetails(document)
    }

    // === EPISODE LIST ===

    override fun episodeListSelector() = "html"

    override fun episodeFromElement(element: Element): SEpisode {
        throw Exception("Not used")
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val url = response.request.url.toString()
        return LK21Parser.parseEpisodeList(document, url)
    }

    // === VIDEO EXTRACTION ===

    override fun videoListSelector() = throw Exception("Not used")

    override fun videoFromElement(element: Element): Video {
        throw Exception("Not used")
    }

    override fun videoUrlParse(document: Document): String {
        throw Exception("Not used")
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val url = response.request.url.toString()
        val videoList = mutableListOf<Video>()

        // Check if this is YouTube trailer
        if (url.contains("youtube.com")) {
            return try {
                YoutubeExtractor(client).videosFromUrl(url)
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting YouTube video", e)
                emptyList()
            }
        }

        // Extract main iframe
        val mainIframe = document.select(LK21Parser.MAIN_IFRAME_SELECTOR).attr("src")
        if (mainIframe.isNotBlank()) {
            try {
                val videos = Lk21Extractor(client, headers).videosFromUrl(mainIframe, "Main Player")
                videoList.addAll(videos)
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting main iframe", e)
            }
        }

        // Extract alternate players
        document.select(LK21Parser.PLAYER_LIST_SELECTOR).forEach { server ->
            val serverName = server.attr("data-server").ifBlank { "Server" }
            val iframeUrl = server.attr("data-url")

            if (iframeUrl.isNotBlank()) {
                try {
                    val videos = Lk21Extractor(client, headers).videosFromUrl(iframeUrl, serverName)
                    videoList.addAll(videos)
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting server: $serverName", e)
                }
            }
        }

        // Sort by preferred quality
        return videoList
            .distinctBy { it.url }
            .sortedWith(compareByDescending {
                it.quality.contains(LK21Config.getPreferredQuality(preferences), ignoreCase = true)
            },)
    }
}
