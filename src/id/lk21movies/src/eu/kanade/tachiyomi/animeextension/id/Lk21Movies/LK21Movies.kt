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

class LK21Movies : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "LK21Movies"

    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val extractor by lazy { Lk21Extractor(client, headers) }

    // ============================= Gateway & Domain =============================

    private val downloadBase = "https://dl.lk21.party/"
    private val playerBase = "https://playeriframe.sbs/iframe/"
    private val posterBase = "https://poster.lk21.party/"

    override val baseUrl: String
        get() = getMainDomain()

    /**
     * Fetch main domain from gateway
     */
    private fun getMainDomain(): String {
        return try {
            val cachedDomain = preferences.getString(PREF_CACHED_DOMAIN_KEY, null)
            val cacheTime = preferences.getLong(PREF_CACHE_TIME_KEY, 0)
            val currentTime = System.currentTimeMillis()

            // Cache valid for 6 hours
            if (cachedDomain != null && (currentTime - cacheTime) < 6 * 60 * 60 * 1000) {
                ReportLog.log("LK21-Domain", "Using cached domain: $cachedDomain", LogLevel.INFO)
                return cachedDomain
            }

            // Fetch new domain from gateway (using Lk21Preferences)
            val gateway = Lk21Preferences.getGatewayUrl(preferences)
            ReportLog.log("LK21-Domain", "Fetching new domain from gateway: $gateway", LogLevel.INFO)
            val response = client.newCall(GET(gateway, headers)).execute()
            val document = response.asJsoup()

            val mainDomain = document.selectFirst("a.cta-button.green-button")
                ?.attr("href")
                ?.trimEnd('/')
                ?: Lk21Preferences.getBaseUrl(preferences, Lk21Preferences.DEFAULT_BASE_URL_MOVIES)

            // Cache the domain
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
        val url = if (page == 1) "$baseUrl/populer/" else "$baseUrl/populer/page/$page"
        ReportLog.log("LK21-Popular", "Loading page $page: $url", LogLevel.INFO)
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "div.gallery-grid article"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val link = element.selectFirst("a[href]")!!
            setUrlWithoutDomain(link.attr("href"))

            title = element.selectFirst("h3.poster-title")?.text() ?: ""
            thumbnail_url = element.selectFirst("picture img")?.attr("src")
                ?: element.selectFirst("img")?.attr("src")
                ?: ""

            ReportLog.log("LK21-Popular", "Parsed: $title", LogLevel.DEBUG)
        }
    }

    // FIX #1 — Duplicate Filter
    override fun popularAnimeParse(response: Response): AnimesPage {
        val seenTitles = mutableSetOf<String>()
        val document = response.asJsoup()
        val animes = document.select(popularAnimeSelector())
            .map { popularAnimeFromElement(it) }
            .filter { anime ->
                val normalized = anime.title.trim().lowercase()
                seenTitles.add(normalized)
            }
        val hasNextPage = document.selectFirst(popularAnimeNextPageSelector()) != null
        return AnimesPage(animes, hasNextPage)
    }

    override fun popularAnimeNextPageSelector(): String = "ul.pagination li a[href*='/page/']"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/latest/" else "$baseUrl/latest/page/$page"
        ReportLog.log("LK21-Latest", "Loading page $page: $url", LogLevel.INFO)
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = "div.gallery-grid article"

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = "ul.pagination li a[href*='/page/']"

    // FIX #1 — Duplicate Filter untuk Latest juga
    override fun latestUpdatesParse(response: Response): AnimesPage {
        val seenTitles = mutableSetOf<String>()
        val document = response.asJsoup()
        val animes = document.select(latestUpdatesSelector())
            .map { latestUpdatesFromElement(it) }
            .filter { anime ->
                val normalized = anime.title.trim().lowercase()
                seenTitles.add(normalized)
            }
        val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()) != null
        return AnimesPage(animes, hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = LK21Filters.getSearchParameters(filters)

        val url = when {
            query.isNotEmpty() -> {
                val searchUrl = if (page == 1) {
                    "$baseUrl/search?s=$query"
                } else {
                    "$baseUrl/search/page/$page?s=$query"
                }
                ReportLog.log("LK21-Search", "Searching: $query (page $page)", LogLevel.INFO)
                searchUrl
            }
            params.genre.isNotEmpty() -> {
                val genreUrl = if (page == 1) {
                    "$baseUrl/genre/${params.genre}/"
                } else {
                    "$baseUrl/genre/${params.genre}/page/$page"
                }
                ReportLog.log("LK21-Search", "Genre filter: ${params.genre}", LogLevel.INFO)
                genreUrl
            }
            params.country.isNotEmpty() -> {
                val countryUrl = if (page == 1) {
                    "$baseUrl/country/${params.country}/"
                } else {
                    "$baseUrl/country/${params.country}/page/$page"
                }
                ReportLog.log("LK21-Search", "Country filter: ${params.country}", LogLevel.INFO)
                countryUrl
            }
            else -> if (page == 1) "$baseUrl/populer/" else "$baseUrl/populer/page/$page"
        }

        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = "div.gallery-grid#results article"

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // FIX #1 — Duplicate Filter untuk Search juga
    override fun searchAnimeParse(response: Response): AnimesPage {
        val seenTitles = mutableSetOf<String>()
        val document = response.asJsoup()
        val animes = document.select(searchAnimeSelector())
            .map { searchAnimeFromElement(it) }
            .filter { anime ->
                val normalized = anime.title.trim().lowercase()
                seenTitles.add(normalized)
            }
        val hasNextPage = document.selectFirst(searchAnimeNextPageSelector()) != null
        return AnimesPage(animes, hasNextPage)
    }

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1[itemprop=name]")?.text() ?: ""

            // FIX #2 — Poster Mismatch
            // Ambil slug dari URL halaman, contoh: /our-universe-2026/ → "our-universe-2026"
            // Lalu validasi poster harus mengandung slug tsb agar tidak keliru
            thumbnail_url = run {
                val pageUrl = document.location()
                val slug = pageUrl.trimEnd('/').substringAfterLast('/')

                ReportLog.log("LK21-Poster", "Page slug: $slug", LogLevel.DEBUG)

                val allImages = document.select("picture img, img[src]")

                // Cari gambar yang src-nya mengandung slug film
                // Contoh: "film-our-universe-2026-lk21" harus mengandung "our-universe-2026"
                val matchedPoster = allImages.firstOrNull { img ->
                    val src = img.attr("src")
                    src.contains(slug, ignoreCase = true)
                }

                if (matchedPoster != null) {
                    ReportLog.log("LK21-Poster", "Matched poster: ${matchedPoster.attr("src")}", LogLevel.INFO)
                    matchedPoster.attr("src")
                } else {
                    // Fallback ke picture img jika tidak ada yang cocok
                    ReportLog.log("LK21-Poster", "No slug match, using fallback poster", LogLevel.WARN)
                    document.selectFirst("picture img")?.attr("src") ?: ""
                }
            }

            genre = document.select("div.genre a")
                .joinToString(", ") { it.text() }

            // Check if series or movie
            val isSeriesElement = document.selectFirst("span.episode")
            status = if (isSeriesElement != null) {
                val episodeText = isSeriesElement.text()
                if (episodeText.contains("complete", ignoreCase = true)) {
                    SAnime.COMPLETED
                } else {
                    SAnime.ONGOING
                }
            } else {
                SAnime.COMPLETED // Movies are always completed
            }

            description = buildString {
                document.selectFirst("div.synopsis")?.text()?.let {
                    append("Synopsis:\n$it\n\n")
                }

                document.selectFirst("span.year")?.text()?.let {
                    append("Year: $it\n")
                }

                document.selectFirst("span.rating")?.text()?.let {
                    append("Rating: $it\n")
                }

                document.selectFirst("span.duration")?.text()?.let {
                    append("Duration: $it\n")
                }

                document.selectFirst("span.label")?.text()?.let {
                    append("Quality: $it\n")
                }
            }

            ReportLog.log("LK21-Detail", "Parsed anime: $title (Status: $status)", LogLevel.INFO)
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector(): String = "ul.episode-list li a, div.main-player"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodeList = mutableListOf<SEpisode>()

        ReportLog.log("LK21-Episodes", "Parsing episodes from: ${response.request.url}", LogLevel.INFO)

        // Check if this is a series with episode list
        val episodeElements = document.select("ul.episode-list li a")

        if (episodeElements.isNotEmpty()) {
            // This is a series
            ReportLog.log("LK21-Episodes", "Found series with ${episodeElements.size} episodes", LogLevel.INFO)

            episodeElements.forEachIndexed { index, element ->
                episodeList.add(
                    SEpisode.create().apply {
                        val episodeUrl = element.attr("href")
                        setUrlWithoutDomain(episodeUrl)

                        val episodeNumber = element.text().trim()
                        name = "Episode $episodeNumber"
                        episode_number = episodeNumber.toFloatOrNull() ?: (index + 1).toFloat()
                        date_upload = System.currentTimeMillis()
                    },
                )
            }
        } else {
            // This is a movie (single episode)
            ReportLog.log("LK21-Episodes", "This is a movie (single episode)", LogLevel.INFO)

            episodeList.add(
                SEpisode.create().apply {
                    setUrlWithoutDomain(response.request.url.toString())
                    name = "Movie"
                    episode_number = 1f
                    date_upload = System.currentTimeMillis()
                },
            )
        }

        return episodeList.reversed()
    }

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        ReportLog.log("LK21-Video", "=== VIDEO PARSE START ===", LogLevel.INFO)
        ReportLog.log("LK21-Video", "Page URL: ${response.request.url}", LogLevel.INFO)

        // Parse player list
        val playerItems = document.select("ul#player-list li a")

        ReportLog.log("LK21-Video", "Found ${playerItems.size} players", LogLevel.INFO)

        playerItems.forEachIndexed { index, element ->
            try {
                val serverName = element.text().trim()
                val dataServer = element.attr("data-server")
                val dataUrl = element.attr("data-url")

                ReportLog.log("LK21-Video", "[$index] Server: $serverName (Type: $dataServer)", LogLevel.DEBUG)

                if (dataUrl.isNotEmpty()) {
                    // Build player URL
                    val playerUrl = when {
                        dataUrl.startsWith("http") -> dataUrl
                        else -> "$playerBase$dataServer/$dataUrl"
                    }

                    ReportLog.log("LK21-Video", "[$index] Player URL: $playerUrl", LogLevel.DEBUG)

                    // Extract video from player
                    extractVideoFromPlayer(playerUrl, videoList, serverName)
                }
            } catch (e: Exception) {
                ReportLog.reportError("LK21-Video", "[$index] Error: ${e.message}")
            }
        }

        // Also check for direct iframe in main-player
        document.selectFirst("iframe#main-player")?.let { iframe ->
            val iframeSrc = iframe.attr("src")
            if (iframeSrc.isNotEmpty()) {
                ReportLog.log("LK21-Video", "Found direct iframe: $iframeSrc", LogLevel.INFO)
                extractVideoFromPlayer(iframeSrc, videoList, "Direct Player")
            }
        }

        ReportLog.log("LK21-Video", "=== TOTAL VIDEOS: ${videoList.size} ===", LogLevel.INFO)

        return videoList.ifEmpty {
            ReportLog.log("LK21-Video", "No videos found, returning WebView option", LogLevel.WARN)
            listOf(Video(response.request.url.toString(), "Open in WebView", response.request.url.toString()))
        }
    }

    /**
     * Extract video from player iframe using Lk21Extractor library
     */
    private fun extractVideoFromPlayer(playerUrl: String, videoList: MutableList<Video>, serverName: String) {
        try {
            ReportLog.log("LK21-Extractor", "Extracting from: $playerUrl (Server: $serverName)", LogLevel.INFO)

            // Use Lk21Extractor library
            val videos = extractor.videosFromUrl(playerUrl, serverName)

            if (videos.isNotEmpty()) {
                ReportLog.log("LK21-Extractor", "Found ${videos.size} video(s) from $serverName", LogLevel.INFO)
                videoList.addAll(videos)
            } else {
                ReportLog.log("LK21-Extractor", "No videos found from $serverName, adding iframe fallback", LogLevel.WARN)
                // Add iframe as fallback if extractor returns empty
                videoList.add(
                    Video(
                        url = playerUrl,
                        quality = "$serverName (Iframe)",
                        videoUrl = playerUrl,
                    ),
                )
            }
        } catch (e: Exception) {
            ReportLog.reportError("LK21-Extractor", "Extraction failed for $playerUrl: ${e.message}")
            e.printStackTrace()
            // Add iframe as error fallback
            videoList.add(
                Video(
                    url = playerUrl,
                    quality = "$serverName (Error)",
                    videoUrl = playerUrl,
                ),
            )
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
            isMovieExtension = true,
        )
    }

    // ============================= Companion ==============================

    companion object {
        private const val PREF_CACHED_DOMAIN_KEY = "cached_domain"
        private const val PREF_CACHE_TIME_KEY = "cache_time"

        private const val PREF_TIMEOUT_KEY = "network_timeout"
        private const val PREF_TIMEOUT_DEFAULT = "90"
    }
}
