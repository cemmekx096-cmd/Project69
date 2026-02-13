package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Extractor
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

class LK21MoviesV2 : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "LK21Movies"
    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val extractor by lazy { Lk21Extractor(client, headers) }

    // Embedded scraper configuration
    private val scraperHelper: ScraperConfigHelper by lazy {
        try {
            ScraperConfigHelper.fromJson(SCRAPER_CONFIG_JSON)
        } catch (e: Exception) {
            ReportLog.reportError("ScraperConfig", "Failed to load config: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val PREF_CACHED_DOMAIN_KEY = "cached_domain"
        private const val PREF_CACHE_TIME_KEY = "cache_time"
        private const val PREF_TIMEOUT_KEY = "network_timeout"
        private const val PREF_TIMEOUT_DEFAULT = "90"

        // Scraper configuration in JSON format
        private const val SCRAPER_CONFIG_JSON = """
{
  "version": "2.0",
  "endpoints": {
    "popular": {
      "path": "/popular",
      "page_format": "/popular/page/{page}"
    },
    "latest": {
      "path": "/latest",
      "page_format": "/latest/page/{page}"
    },
    "search": {
      "path": "/?s={query}",
      "page_format": "/page/{page}/?s={query}"
    },
    "genre": {
      "path": "/genre/{genre}",
      "page_format": "/genre/{genre}/page/{page}"
    },
    "country": {
      "path": "/country/{country}",
      "page_format": "/country/{country}/page/{page}"
    }
  },
  "selectors": {
    "popular": {
      "container": "ul.sliders",
      "item": "li.slider:not(:has(span.episode))",
      "title": "h3.poster-title",
      "url": "a",
      "thumbnail": "img",
      "thumbnail_attribute": "src",
      "url_attribute": "href",
      "pagination": "div.pagination a.next"
    },
    "latest": {
      "container": "article.widget",
      "item": "figure",
      "title": "h3.poster-title",
      "url": "a",
      "thumbnail": "picture img",
      "thumbnail_attribute": "src",
      "url_attribute": "href",
      "rating": "span.rating",
      "year": "span.year",
      "quality": "span.label",
      "duration": "span.duration",
      "genre": "div.genre",
      "pagination": "div.pagination a.next"
    },
    "search": {
      "container": "div.main-section",
      "item": "figure",
      "title": "h3.poster-title",
      "url": "a",
      "thumbnail": "picture img",
      "thumbnail_attribute": "src",
      "url_attribute": "href",
      "pagination": "div.pagination a.next"
    },
    "detail": {
      "title": "h1[itemprop=name]",
      "poster_primary": "picture img",
      "poster_fallback": "img[itemprop=image]",
      "poster_attribute": "src",
      "genre": "div.genre a",
      "synopsis": "div.synopsis",
      "year": "span.year",
      "rating": "span.rating",
      "duration": "span.duration",
      "quality": "span.label",
      "episode_indicator": "span.episode",
      "episode_list": "ul.episode-list li a",
      "player_list": "ul#player-list li a",
      "player_iframe": "iframe#main-player"
    },
    "player": {
      "server_name": "text",
      "data_server": "data-server",
      "data_url": "data-url"
    }
  },
  "poster_matching": {
    "enabled": true,
    "validation": {
      "check_slug_in_poster": true,
      "check_title_in_poster": true,
      "ignore_case": true,
      "remove_special_chars": true,
      "allowed_extensions": ["jpg", "jpeg", "png", "webp"]
    }
  },
  "data_normalization": {
    "title": {
      "trim": true,
      "remove_year_suffix": false,
      "decode_html_entities": true
    },
    "thumbnail": {
      "prefer_webp": false,
      "prefer_high_quality": true,
      "validate_url": true
    },
    "url": {
      "ensure_absolute": true,
      "trim_trailing_slash": false
    }
  },
  "filtering": {
    "duplicate_detection": {
      "enabled": true,
      "method": "normalized_title",
      "case_sensitive": false,
      "trim_whitespace": true
    },
    "exclude_series": {
      "enabled_for_movies": true,
      "indicator": "span.episode"
    }
  }
}
        """
    }

    // ============================= Gateway & Domain =============================

    override val baseUrl: String get() = getMainDomain()

    private fun getMainDomain(): String {
        return try {
            val cachedDomain = preferences.getString(PREF_CACHED_DOMAIN_KEY, null)
            val cacheTime = preferences.getLong(PREF_CACHE_TIME_KEY, 0)
            val currentTime = System.currentTimeMillis()

            if (cachedDomain != null && (currentTime - cacheTime) < 6 * 60 * 60 * 1000) {
                ReportLog.log("LK21-Domain", "Using cached domain: $cachedDomain", LogLevel.INFO)
                return cachedDomain
            }

            val gateway = Lk21Preferences.getGatewayUrl(preferences)
            ReportLog.log("LK21-Domain", "Fetching new domain from gateway: $gateway", LogLevel.INFO)
            val response = client.newCall(GET(gateway, headers)).execute()
            val document = response.asJsoup()

            val mainDomain = document.selectFirst("a.cta-button.green-button")
                ?.attr("href")
                ?.trimEnd('/')
                ?: Lk21Preferences.getBaseUrl(preferences, Lk21Preferences.DEFAULT_BASE_URL_MOVIES)

            preferences.edit()
                .putString(PREF_CACHED_DOMAIN_KEY, mainDomain)
                .putLong(PREF_CACHE_TIME_KEY, currentTime)
                .apply()

            ReportLog.log("LK21-Domain", "New domain fetched: $mainDomain", LogLevel.INFO)
            mainDomain
        } catch (e: Exception) {
            ReportLog.reportError("LK21-Domain", "Failed to fetch domain: ${e.message}")
            Lk21Preferences.getBaseUrl(preferences, Lk21Preferences.DEFAULT_BASE_URL_MOVIES)
        }
    }

    override val client: OkHttpClient
        get() {
            val timeoutSeconds = preferences.getString(PREF_TIMEOUT_KEY, PREF_TIMEOUT_DEFAULT)!!
                .toLongOrNull() ?: 90L

            return network.client.newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()
        }

    override fun headersBuilder(): Headers.Builder {
        val userAgent = Lk21Preferences.getUserAgent(preferences)
        return super.headersBuilder().apply {
            add("User-Agent", userAgent)
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        }
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        val url = scraperHelper.buildUrl(baseUrl, "popular", page)
        ReportLog.log("LK21-Popular", "Loading page $page: $url", LogLevel.INFO)
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String {
        return scraperHelper.getSelectorConfig("popular").item
    }

    override fun popularAnimeFromElement(element: Element): SAnime {
        val selector = scraperHelper.getSelectorConfig("popular")
        val parsed = scraperHelper.parseItemFromElement(element, selector, baseUrl)
            ?: throw Exception("Failed to parse popular item")

        return SAnime.create().apply {
            setUrlWithoutDomain(parsed.url.removePrefix(baseUrl))
            title = parsed.title
            thumbnail_url = parsed.thumbnail

            ReportLog.log("LK21-Popular", "Parsed: ${parsed.title}", LogLevel.DEBUG)
        }
    }

    private val seenTitles = mutableSetOf<String>()

    override fun popularAnimeParse(response: Response): AnimesPage {
        seenTitles.clear()
        val document = response.asJsoup()
        val selector = scraperHelper.getSelectorConfig("popular")
        
        val animes = document.select(selector.item)
            .filterNot { scraperHelper.shouldExclude(it, forMovies = true) }
            .mapNotNull { element ->
                try {
                    val anime = popularAnimeFromElement(element)
                    val normalized = anime.title.trim().lowercase()
                    if (seenTitles.add(normalized)) anime else null
                } catch (e: Exception) {
                    ReportLog.reportError("LK21-Popular", "Parse error: ${e.message}")
                    null
                }
            }
        
        val hasNextPage = document.selectFirst(selector.pagination) != null
        return AnimesPage(animes, hasNextPage)
    }

    override fun popularAnimeNextPageSelector(): String {
        return scraperHelper.getSelectorConfig("popular").pagination
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        // FIX: Now using separate /latest endpoint!
        val url = scraperHelper.buildUrl(baseUrl, "latest", page)
        ReportLog.log("LK21-Latest", "Loading page $page: $url", LogLevel.INFO)
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String {
        // FIX: Now using different selector for latest!
        return scraperHelper.getSelectorConfig("latest").item
    }

    override fun latestUpdatesFromElement(element: Element): SAnime {
        val selector = scraperHelper.getSelectorConfig("latest")
        val parsed = scraperHelper.parseItemFromElement(element, selector, baseUrl)
            ?: throw Exception("Failed to parse latest item")

        return SAnime.create().apply {
            setUrlWithoutDomain(parsed.url.removePrefix(baseUrl))
            title = parsed.title
            thumbnail_url = parsed.thumbnail

            ReportLog.log("LK21-Latest", "Parsed: ${parsed.title}", LogLevel.DEBUG)
        }
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        seenTitles.clear()
        val document = response.asJsoup()
        val selector = scraperHelper.getSelectorConfig("latest")
        
        val animes = document.select(selector.item)
            .filterNot { scraperHelper.shouldExclude(it, forMovies = true) }
            .mapNotNull { element ->
                try {
                    val anime = latestUpdatesFromElement(element)
                    val normalized = anime.title.trim().lowercase()
                    if (seenTitles.add(normalized)) anime else null
                } catch (e: Exception) {
                    ReportLog.reportError("LK21-Latest", "Parse error: ${e.message}")
                    null
                }
            }
        
        val hasNextPage = document.selectFirst(selector.pagination) != null
        return AnimesPage(animes, hasNextPage)
    }

    override fun latestUpdatesNextPageSelector(): String {
        return scraperHelper.getSelectorConfig("latest").pagination
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = LK21Filters.getSearchParameters(filters)

        val url = when {
            query.isNotEmpty() -> scraperHelper.buildUrl(baseUrl, "search", page, query)
            params.genre.isNotEmpty() -> {
                if (page == 1) "$baseUrl/genre/${params.genre}"
                else "$baseUrl/genre/${params.genre}/page/$page"
            }
            params.country.isNotEmpty() -> {
                if (page == 1) "$baseUrl/country/${params.country}"
                else "$baseUrl/country/${params.country}/page/$page"
            }
            else -> scraperHelper.buildUrl(baseUrl, "popular", page)
        }

        ReportLog.log("LK21-Search", "Search URL: $url", LogLevel.INFO)
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String {
        return scraperHelper.getSelectorConfig("search").item
    }

    override fun searchAnimeFromElement(element: Element): SAnime {
        val selector = scraperHelper.getSelectorConfig("search")
        val parsed = scraperHelper.parseItemFromElement(element, selector, baseUrl)
            ?: throw Exception("Failed to parse search item")

        return SAnime.create().apply {
            setUrlWithoutDomain(parsed.url.removePrefix(baseUrl))
            title = parsed.title
            thumbnail_url = parsed.thumbnail
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        seenTitles.clear()
        val document = response.asJsoup()
        val selector = scraperHelper.getSelectorConfig("search")
        
        val animes = document.select(selector.item)
            .filterNot { scraperHelper.shouldExclude(it, forMovies = true) }
            .mapNotNull { element ->
                try {
                    val anime = searchAnimeFromElement(element)
                    val normalized = anime.title.trim().lowercase()
                    if (seenTitles.add(normalized)) anime else null
                } catch (e: Exception) {
                    null
                }
            }
        
        val hasNextPage = document.selectFirst(selector.pagination) != null
        return AnimesPage(animes, hasNextPage)
    }

    override fun searchAnimeNextPageSelector(): String {
        return scraperHelper.getSelectorConfig("search").pagination
    }

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        val detailSelector = scraperHelper.scraperHelper.config.selectors.detail
        
        return SAnime.create().apply {
            title = document.selectFirst(detailSelector.title)?.text() ?: ""

            // FIX: Smart poster matching using config helper
            thumbnail_url = scraperHelper.matchPoster(document, document.location(), title)

            genre = document.select(detailSelector.genre)
                .joinToString(", ") { it.text() }

            val isSeriesElement = document.selectFirst(detailSelector.episode_indicator)
            status = if (isSeriesElement != null) {
                val episodeText = isSeriesElement.text()
                if (episodeText.contains("complete", ignoreCase = true)) {
                    SAnime.COMPLETED
                } else {
                    SAnime.ONGOING
                }
            } else {
                SAnime.COMPLETED
            }

            description = buildString {
                document.selectFirst(detailSelector.synopsis)?.text()?.let {
                    append("Synopsis:\n$it\n\n")
                }
                document.selectFirst(detailSelector.year)?.text()?.let {
                    append("Year: $it\n")
                }
                document.selectFirst(detailSelector.rating)?.text()?.let {
                    append("Rating: $it\n")
                }
                document.selectFirst(detailSelector.duration)?.text()?.let {
                    append("Duration: $it\n")
                }
                document.selectFirst(detailSelector.quality)?.text()?.let {
                    append("Quality: $it\n")
                }
            }

            ReportLog.log("LK21-Detail", "Parsed: $title (Poster: $thumbnail_url)", LogLevel.INFO)
        }
    }

    // ============================== Episodes ==============================
    // (Keep existing episode logic - it's already good)

    override fun episodeListSelector(): String = "ul.episode-list li a, div.main-player"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodeList = mutableListOf<SEpisode>()

        val episodeElements = document.select("ul.episode-list li a")

        if (episodeElements.isNotEmpty()) {
            episodeElements.forEachIndexed { index, element ->
                episodeList.add(
                    SEpisode.create().apply {
                        setUrlWithoutDomain(element.attr("href"))
                        val episodeNumber = element.text().trim()
                        name = "Episode $episodeNumber"
                        episode_number = episodeNumber.toFloatOrNull() ?: (index + 1).toFloat()
                        date_upload = System.currentTimeMillis()
                    }
                )
            }
        } else {
            episodeList.add(
                SEpisode.create().apply {
                    setUrlWithoutDomain(response.request.url.toString())
                    name = "Movie"
                    episode_number = 1f
                    date_upload = System.currentTimeMillis()
                }
            )
        }

        return episodeList.reversed()
    }

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ============================ Video Links =============================
    // (Keep existing video logic - it's using extractor library)

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        val playerItems = document.select("ul#player-list li a")

        playerItems.forEachIndexed { index, element ->
            try {
                val serverName = element.text().trim()
                val dataUrl = element.attr("data-url")

                if (dataUrl.isNotEmpty()) {
                    val playerUrl = if (dataUrl.startsWith("http")) dataUrl 
                                   else "$playerBase${element.attr("data-server")}/$dataUrl"
                    
                    extractVideoFromPlayer(playerUrl, videoList, serverName)
                }
            } catch (e: Exception) {
                ReportLog.reportError("LK21-Video", "Error: ${e.message}")
            }
        }

        document.selectFirst("iframe#main-player")?.let { iframe ->
            val iframeSrc = iframe.attr("src")
            if (iframeSrc.isNotEmpty()) {
                extractVideoFromPlayer(iframeSrc, videoList, "Direct Player")
            }
        }

        return videoList.ifEmpty {
            listOf(Video(response.request.url.toString(), "Open in WebView", response.request.url.toString()))
        }
    }

    private val playerBase = "https://playeriframe.sbs/iframe/"

    private fun extractVideoFromPlayer(playerUrl: String, videoList: MutableList<Video>, serverName: String) {
        try {
            val videos = extractor.videosFromUrl(playerUrl, serverName)
            if (videos.isNotEmpty()) {
                videoList.addAll(videos)
            } else {
                videoList.add(Video(playerUrl, "$serverName (Iframe)", playerUrl))
            }
        } catch (e: Exception) {
            videoList.add(Video(playerUrl, "$serverName (Error)", playerUrl))
        }
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = LK21Filters.getFilterList()

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Lk21Preferences.setupPreferences(
            screen = screen,
            preferences = preferences,
            defaultBaseUrl = Lk21Preferences.DEFAULT_BASE_URL_MOVIES,
            isMovieExtension = true
        )
    }
}
