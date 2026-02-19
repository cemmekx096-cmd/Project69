package eu.kanade.tachiyomi.animeextension.id.lk21series

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
import eu.kanade.tachiyomi.lib.cloudflareinterceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Extractor
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Preferences
import eu.kanade.tachiyomi.lib.lk21extractor.ReportLog
import eu.kanade.tachiyomi.lib.lk21extractor.LogLevel
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

class LK21Series : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "LK21Series"

    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val extractor by lazy { Lk21Extractor(client, headers) }

    override val baseUrl = "https://tv3.nontondrama.my"


    /**
     * Fetch main domain from gateway
     */

    override val client: OkHttpClient
        get() {
            val timeoutSeconds = preferences.getString(PREF_TIMEOUT_KEY, PREF_TIMEOUT_DEFAULT)!!
                .toLongOrNull() ?: 90L

            return network.client.newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .addInterceptor(CloudflareInterceptor(network.client))
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

            title = element.selectFirst("h3.poster-title[itemprop=name]")?.text()
                ?: element.selectFirst("h3.poster-title")?.ownText()
                ?: ""
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
    // Search disabled due to Cloudflare protection on API
    
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        throw UnsupportedOperationException("Search not supported")
    }
    
    override fun searchAnimeSelector(): String = throw UnsupportedOperationException("Search not supported")
    
    override fun searchAnimeFromElement(element: Element): SAnime = throw UnsupportedOperationException("Search not supported")
    
    override fun searchAnimeNextPageSelector(): String? = null
    
    override fun searchAnimeParse(response: Response): AnimesPage = throw UnsupportedOperationException("Search not supported")


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

            genre = document.select("div.tag-list span.tag a[href*=/genre/]")
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
                document.selectFirst("div.synopsis expanded, div.synopsis")?.text()?.let {
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

                // Tambah link trailer jika ada
                document.selectFirst("a.yt-lightbox[href*=youtube]")?.attr("href")?.let {
                    append("\nTrailer: $it")
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

        // Kumpulkan semua player URL dulu
        val playerItems = document.select("ul#player-list li a")
        ReportLog.log("LK21-Video", "Found ${playerItems.size} players", LogLevel.INFO)

        data class PlayerEntry(val url: String, val name: String)

        val playerEntries = mutableListOf<PlayerEntry>()

        playerItems.forEach { element ->
            val serverName = element.text().trim()
            val dataServer = element.attr("data-server")
            val dataUrl = element.attr("data-url")

            if (dataUrl.isNotEmpty()) {
                val playerUrl = when {
                    dataUrl.startsWith("http") -> dataUrl
                    else -> "$dataServer/$dataUrl" // format: "cast/slug", "turbovip/slug", "p2p/slug"
                }
                playerEntries.add(PlayerEntry(playerUrl, serverName))
                ReportLog.log("LK21-Video", "Found player: $serverName → $playerUrl", LogLevel.DEBUG)
            }
        }

        // Juga tambahkan direct iframe jika ada
        document.selectFirst("iframe#main-player")?.let { iframe ->
            val iframeSrc = iframe.attr("src")
            if (iframeSrc.isNotEmpty()) {
                playerEntries.add(PlayerEntry(iframeSrc, "Direct Player"))
                ReportLog.log("LK21-Video", "Found direct iframe: $iframeSrc", LogLevel.INFO)
            }
        }

        // Sort player: Cast → TurboVIP → Hydrax → P2P → lainnya
        val sortedEntries = playerEntries.sortedBy { entry ->
            when {
                entry.url.contains("cast") -> 1
                entry.url.contains("turbovip") -> 2
                entry.url.contains("hydrax") -> 3
                entry.url.contains("p2p") -> 4
                else -> 5
            }
        }

        // Extract video dari setiap player
        sortedEntries.forEachIndexed { index, entry ->
            try {
                ReportLog.log("LK21-Video", "[$index] Extracting: ${entry.name} → ${entry.url}", LogLevel.DEBUG)
                val videos = extractor.videosFromUrl(entry.url, entry.name)
                if (videos.isNotEmpty()) {
                    ReportLog.log("LK21-Video", "[$index] Found ${videos.size} video(s) from ${entry.name}", LogLevel.INFO)
                    videoList.addAll(videos)
                } else {
                    ReportLog.log("LK21-Video", "[$index] No videos from ${entry.name}, adding iframe fallback", LogLevel.WARN)
                    videoList.add(Video(entry.url, "${entry.name} (Iframe)", entry.url))
                }
            } catch (e: Exception) {
                ReportLog.reportError("LK21-Video", "[$index] Error: ${e.message}")
            }
        }

        ReportLog.log("LK21-Video", "=== TOTAL VIDEOS: ${videoList.size} ===", LogLevel.INFO)

        return videoList.ifEmpty {
            ReportLog.log("LK21-Video", "No videos found, returning WebView option", LogLevel.WARN)
            listOf(Video(response.request.url.toString(), "Open in WebView", response.request.url.toString()))
        }
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList() // No filters for series

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Lk21Preferences.setupPreferences(
            screen = screen,
            preferences = preferences,
            defaultBaseUrl = Lk21Preferences.DEFAULT_BASE_URL_SERIES,
            isMovieExtension = false,
        )
    }

    // ============================= Companion ==============================

    companion object {
        private const val PREF_TIMEOUT_KEY = "network_timeout"
        private const val PREF_TIMEOUT_DEFAULT = "90"
    }
}
