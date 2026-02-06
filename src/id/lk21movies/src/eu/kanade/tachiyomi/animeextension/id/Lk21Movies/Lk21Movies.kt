package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.lk21extractor.CountryFilter
import eu.kanade.tachiyomi.lib.lk21extractor.GenreFilter
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Common
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21DomainFetcher
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Extractor
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Filters
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Preferences
import eu.kanade.tachiyomi.lib.lk21extractor.YearFilter
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

class Lk21Movies : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "LK21 Movies"
    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // Dynamic base URL dari domain fetcher
    override val baseUrl: String
        get() {
            // Check cached domain
            val cached = preferences.getString(Lk21Preferences.PREF_BASE_URL_KEY, null)
            val cacheTime = preferences.getLong("domain_cache_time", 0)
            val cacheValid = System.currentTimeMillis() - cacheTime < 24 * 60 * 60 * 1000 // 24 hours

            return if (cached != null && cacheValid) {
                Log.d(TAG, "Using cached domain: $cached")
                cached
            } else {
                // Fetch fresh domain
                Log.d(TAG, "Fetching fresh domain...")
                val domain = Lk21DomainFetcher.fetchDomainWithFallback(
                    client,
                    Lk21Preferences.DEFAULT_BASE_URL_MOVIES,
                )

                // Cache domain
                preferences.edit()
                    .putString(Lk21Preferences.PREF_BASE_URL_KEY, domain)
                    .putLong("domain_cache_time", System.currentTimeMillis())
                    .apply()

                domain
            }
        }

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val lk21Extractor by lazy { Lk21Extractor(client) }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", Lk21Preferences.getUserAgent(preferences))
        .add("Referer", baseUrl)

    // ========================== POPULAR ANIME (Movies) ==========================

    override fun popularAnimeSelector() = "article[itemscope][itemtype*='Movie']"

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/populer/page/$page/", headers)

    override fun popularAnimeFromElement(element: Element): SAnime {
        // FILTER: Check for series badge
        val episodeBadge = element.selectFirst("span.episode.complete, span.episode.ongoing")
        if (episodeBadge != null) {
            Log.d(TAG, "Skipping series (has episode badge)")
            return SAnime.create() // Empty anime = akan di-skip
        }

        return SAnime.create().apply {
            val link = element.selectFirst("figure > a") ?: return@apply
            val href = link.attr("abs:href")

            // Skip jika URL kosong
            if (href.isEmpty()) return@apply

            setUrlWithoutDomain(href)

            // Parse title dari link atau img alt
            title = link.attr("title").ifEmpty {
                element.selectFirst("img")?.attr("alt") ?: ""
            }.trim()

            // Clean title
            if (title.isNotEmpty()) {
                title = Lk21Common.cleanTitle(title)
            }

            // Thumbnail dengan fallback
            thumbnail_url = element.selectFirst("picture source[type='image/webp']")
                ?.attr("srcset")
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
                ?.split(" ")
                ?.firstOrNull()
                ?: element.selectFirst("img")?.attr("abs:src")

            // Parse year dari badge
            val yearText = element.selectFirst("span.year")?.text()
            if (!yearText.isNullOrEmpty() && !title.contains(yearText)) {
                title = "$title ($yearText)"
            }

            Log.d(TAG, "Parsed movie: $title")
        }
    }

    override fun popularAnimeNextPageSelector() = "a.next, .pagination a:contains(Next)"

    // ========================== LATEST ANIME (Movies) ==========================

    override fun latestUpdatesSelector() = popularAnimeSelector()

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/latest/page/$page/", headers)

    override fun latestUpdatesFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // ========================== SEARCH & FILTERS ==========================

    override fun searchAnimeSelector() = popularAnimeSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        // Build URL berdasarkan filter priority: Genre > Country > Year
        val genreFilter = filters.findInstance<GenreFilter>()
        val yearFilter = filters.findInstance<YearFilter>()
        val countryFilter = filters.findInstance<CountryFilter>()

        val selectedGenre = genreFilter?.selected()
        val selectedYear = yearFilter?.selected()
        val selectedCountry = countryFilter?.selected()

        val url = when {
            // Priority 1: Genre filter
            selectedGenre != null -> {
                Log.d(TAG, "Search with genre: $selectedGenre")
                "$baseUrl/genre/$selectedGenre/page/$page/"
            }
            // Priority 2: Country filter
            selectedCountry != null -> {
                Log.d(TAG, "Search with country: $selectedCountry")
                "$baseUrl/country/$selectedCountry/page/$page/"
            }
            // Priority 3: Year filter
            selectedYear != null -> {
                Log.d(TAG, "Search with year: $selectedYear")
                "$baseUrl/year/$selectedYear/page/$page/"
            }
            // No filter: Search by query
            query.isNotEmpty() -> {
                Log.d(TAG, "Search with query: $query")
                val cleanQuery = query.trim().replace(" ", "+")
                "$baseUrl/search/$cleanQuery/page/$page/"
            }
            // Default: Popular
            else -> {
                Log.d(TAG, "Search: fallback to popular")
                "$baseUrl/populer/page/$page/"
            }
        }

        return GET(url, headers)
    }

    override fun searchAnimeFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    override fun getFilterList(): AnimeFilterList {
        return Lk21Filters.getFilterList(client, baseUrl, preferences)
    }

    // ========================== ANIME DETAILS ==========================

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        // Parse title
        title = document.selectFirst("h1")?.text()?.trim() ?: ""
        if (title.isNotEmpty()) {
            title = Lk21Common.cleanTitle(title)
        }

        // Parse rating dan add ke description
        val ratingText = document.selectFirst(".info-tag span strong")?.text()

        // Parse synopsis
        val synopsisDiv = document.selectFirst(".synopsis, .meta-info .synopsis")
        description = synopsisDiv?.text()?.trim()?.ifEmpty {
            "Tidak ada sinopsis tersedia."
        } ?: "Tidak ada sinopsis tersedia."

        // Add rating to description
        if (!ratingText.isNullOrEmpty()) {
            description = "⭐ Rating: $ratingText/10\n\n$description"
        }

        // Parse genre
        val genreElements = document.select(".tag-list span.tag a, div[class*='genre'] a")
        genre = if (genreElements.isNotEmpty()) {
            genreElements.joinToString(", ") { it.text().trim() }
        } else {
            ""
        }

        // Parse thumbnail
        thumbnail_url = document.selectFirst("picture source[type='image/webp']")
            ?.attr("srcset")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.split(" ")
            ?.firstOrNull()
            ?: document.selectFirst("meta[property='og:image']")?.attr("content")

        // Status (movies always completed)
        status = SAnime.COMPLETED

        Log.d(TAG, "Parsed details: $title")
    }

    // ========================== EPISODE LIST (Movie = Single Episode) ==========================

    override fun episodeListParse(response: Response): List<SEpisode> {
        // Movie hanya punya 1 "episode"
        return listOf(
            SEpisode.create().apply {
                name = "Movie"
                episode_number = 1f
                setUrlWithoutDomain(response.request.url.toString())
                date_upload = System.currentTimeMillis()
            },
        )
    }

    override fun episodeListSelector() = throw UnsupportedOperationException()
    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ========================== VIDEO EXTRACTION ==========================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        Log.d(TAG, "Extracting videos from: ${response.request.url}")

        // Method 1: Player options (select/dropdown)
        document.select("select#player-select option, select.mirror option").forEach { option ->
            val url = option.attr("value")
            val serverName = option.text().trim().ifEmpty {
                "Server ${option.attr("data-server")}"
            }

            if (url.isNotEmpty() && url.startsWith("http")) {
                Log.d(TAG, "Found player option: $serverName")
                videoList.addAll(lk21Extractor.videosFromUrl(url, serverName))
            }
        }

        // Method 2: Player list (ul/li)
        document.select("ul#player-list li a, ul#player_list li a").forEach { link ->
            val url = link.attr("abs:href").ifEmpty { link.attr("data-url") }
            val serverName = link.text().trim()

            if (url.isNotEmpty() && url.startsWith("http")) {
                Log.d(TAG, "Found player link: $serverName")
                videoList.addAll(lk21Extractor.videosFromUrl(url, serverName))
            }
        }

        // Method 3: Download buttons
        document.select(".movie-action a[href*='dl.lk21'], a.btn-download").forEach { btn ->
            val url = btn.attr("abs:href")
            if (url.isNotEmpty() && url.startsWith("http")) {
                Log.d(TAG, "Found download link")
                videoList.addAll(lk21Extractor.videosFromUrl(url, "Download"))
            }
        }

        Log.d(TAG, "Total videos found: ${videoList.size}")

        // Sort by preferred quality
        val preferredQuality = preferences.getString(
            Lk21Preferences.PREF_QUALITY_KEY,
            "720",
        )!!

        val sortedList = videoList.sortedByDescending { video ->
            if (video.quality.contains("${preferredQuality}p", ignoreCase = true)) {
                1000
            } else {
                when {
                    video.quality.contains("1080p", ignoreCase = true) -> 100
                    video.quality.contains("720p", ignoreCase = true) -> 90
                    video.quality.contains("480p", ignoreCase = true) -> 80
                    video.quality.contains("360p", ignoreCase = true) -> 70
                    else -> 0
                }
            }
        }

        return sortedList
    }

    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ========================== PREFERENCES ==========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Lk21Preferences.setupPreferences(
            screen = screen,
            preferences = preferences,
            defaultBaseUrl = Lk21Preferences.DEFAULT_BASE_URL_MOVIES,
            isMovieExtension = true,
        )
    }

    // ========================== HELPER ==========================

    private inline fun <reified T> AnimeFilterList.findInstance(): T? {
        return this.filterIsInstance<T>().firstOrNull()
    }

    companion object {
        private const val TAG = "Lk21Movies"
    }
}
