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
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Common
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Extractor
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Preferences
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

    // Base URL dari preferences
    override val baseUrl: String
        get() = Lk21Preferences.getBaseUrl(preferences, Lk21Preferences.DEFAULT_BASE_URL_MOVIES)

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val lk21Extractor by lazy { Lk21Extractor(client) }

    // Custom headers
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", Lk21Preferences.getUserAgent(preferences))
        .add("Referer", baseUrl)

    // ========================== POPULAR ANIME (Movies) ==========================

    override fun popularAnimeSelector() = "article.item-infinite, article.f-item, div.grid-archive li"

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/populer/page/$page/", headers)

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val link = element.selectFirst("a") ?: return@apply
            val href = link.attr("abs:href")

            // FILTER 1: Skip jika URL mengandung redirect ke drama
            if (Lk21Common.isSeriesUrl(href)) {
                android.util.Log.d("Lk21Movies", "Skipping series URL: $href")
                return@apply
            }

            // FILTER 2: Check jika ada link redirect di dalam element
            val redirectLink = element.selectFirst("a[href*=nontondrama], a:contains(Buka Sekarang)")
            if (redirectLink != null) {
                android.util.Log.d("Lk21Movies", "Skipping element with redirect link")
                return@apply
            }

            setUrlWithoutDomain(href)
            title = Lk21Common.cleanTitle(
                element.select("h2, h3, .entry-title").text().trim(),
            )

            // Thumbnail dengan fallback
            thumbnail_url = element.select("img").attr("abs:src").ifEmpty {
                element.select("img").attr("abs:data-src")
            }

            // Parse metadata jika tersedia
            val meta = element.select(".meta, .item-meta").text()
            if (meta.isNotEmpty()) {
                val year = Lk21Common.extractYear(meta)
                val quality = Lk21Common.parseQuality(meta)

                if (year != null) {
                    title = "$title ($year)"
                }
            }
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

    // ========================== SEARCH ==========================

    override fun searchAnimeSelector() = popularAnimeSelector()

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val cleanQuery = query.trim().replace(" ", "+")
        return GET("$baseUrl/search/$cleanQuery/page/$page/", headers)
    }

    override fun searchAnimeFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // ========================== ANIME DETAILS ==========================

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        // FINAL CHECK: Pastikan ini bukan series
        val redirectLink = document.selectFirst("a[href*=nontondrama], a:contains(Buka Sekarang)")
        if (redirectLink != null) {
            android.util.Log.w("Lk21Movies", "Details page has redirect - this is a series!")
            title = "Series (Not Supported)"
            description = "This is a series. Please use LK21 Drama extension instead."
            status = SAnime.UNKNOWN
            return@apply
        }

        // Parse title dengan cleaning
        title = Lk21Common.cleanTitle(
            document.select("h1.entry-title, h1").text().trim(),
        )

        // Parse description/synopsis
        description = document.select(
            ".entry-content p, .synopsis, .description, .film-content",
        ).text().trim().ifEmpty {
            "Tidak ada sinopsis tersedia."
        }

        // Parse genre
        val genreList = document.select(".genre-info a, .cat-links a, .taxonomy a")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        genre = if (genreList.isNotEmpty()) {
            Lk21Common.normalizeGenres(genreList.joinToString(", "))
        } else {
            ""
        }

        // Parse thumbnail dengan fallback
        thumbnail_url = document.select("picture img, .thumb img, .poster img, meta[property='og:image']")
            .attr("abs:src")
            .ifEmpty {
                document.select("meta[property='og:image']").attr("abs:content")
            }

        // Parse metadata tambahan
        val metaText = document.select(".film-detail, .movie-info, .meta-info").text()

        // Status (default COMPLETED untuk movie)
        status = SAnime.COMPLETED

        // Extract year jika ada
        val year = Lk21Common.extractYear(metaText)
        if (year != null && !title.contains(year)) {
            title = "$title ($year)"
        }

        // Extract rating jika ada
        val rating = Lk21Common.extractRating(metaText)
        if (rating != null) {
            description = "⭐ Rating: $rating/10\n\n$description"
        }

        android.util.Log.d("Lk21Movies", "Parsed movie: $title")
    }

    // ========================== EPISODE LIST (Movie = Single Episode) ==========================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()

        // SAFETY CHECK: Jika terdeteksi episode list, ini bukan movie
        val episodeElements = document.select("ul.list-episode li, .episode-list a")
        if (episodeElements.isNotEmpty()) {
            android.util.Log.w("Lk21Movies", "Episode list detected - this is a series!")
            return emptyList()
        }

        // Movie hanya punya 1 "episode"
        return listOf(
            SEpisode.create().apply {
                name = "Movie"
                episode_number = 1f
                setUrlWithoutDomain(document.location())
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

        android.util.Log.d("Lk21Movies", "Extracting videos from: ${response.request.url}")

        // Method 1: Extract dari player options (select/dropdown)
        document.select("select#player-select option, select.mirror option").forEach { option ->
            val url = option.attr("value")
            val serverName = option.text().trim().ifEmpty { "Server ${option.attr("data-server")}" }

            if (url.isNotEmpty() && url.startsWith("http")) {
                android.util.Log.d("Lk21Movies", "Found player option: $serverName -> $url")
                videoList.addAll(lk21Extractor.videosFromUrl(url, serverName))
            }
        }

        // Method 2: Extract dari player list (ul/li)
        document.select("ul#player-list li a, ul#player_list li a").forEach { link ->
            val url = link.attr("abs:href").ifEmpty { link.attr("data-url") }
            val serverName = link.text().trim()

            if (url.isNotEmpty() && url.startsWith("http")) {
                android.util.Log.d("Lk21Movies", "Found player link: $serverName -> $url")
                videoList.addAll(lk21Extractor.videosFromUrl(url, serverName))
            }
        }

        // Method 3: Extract dari download buttons
        document.select("a.btn-download, .download-link a").forEach { btn ->
            val url = btn.attr("abs:href")
            if (url.isNotEmpty() && url.startsWith("http")) {
                android.util.Log.d("Lk21Movies", "Found download button -> $url")
                videoList.addAll(lk21Extractor.videosFromUrl(url, "Download"))
            }
        }

        // Filter berdasarkan preferences
        val playerFilter = Lk21Preferences.getPlayerFilter(preferences)
        val filteredList = when (playerFilter) {
            "no_iframe" -> videoList.filter { !it.quality.contains("Iframe", ignoreCase = true) }
            "direct_only" -> videoList.filter {
                it.quality.contains("720p") ||
                    it.quality.contains("1080p") ||
                    it.quality.contains("480p") ||
                    it.quality.contains("360p")
            }
            else -> videoList
        }

        android.util.Log.d("Lk21Movies", "Total videos found: ${filteredList.size}")

// Sort videos by preferred quality
        val preferredQuality = preferences.getString(
            Lk21Preferences.PREF_QUALITY_KEY,
            "720",
        )!!

        val sortedList = filteredList.sortedByDescending { video ->
            // Priority 1: Match preferred quality (highest priority)
            if (video.quality.contains("${preferredQuality}p", ignoreCase = true)) {
                1000
            } else {
                // Priority 2: Quality ranking (for non-matching videos)
                when {
                    video.quality.contains("1080p", ignoreCase = true) -> 100
                    video.quality.contains("720p", ignoreCase = true) -> 90
                    video.quality.contains("480p", ignoreCase = true) -> 80
                    video.quality.contains("360p", ignoreCase = true) -> 70
                    else -> 0
                }
            }
        }

        android.util.Log.d("Lk21Movies", "Sorted by preferred quality: $preferredQuality")

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
}
