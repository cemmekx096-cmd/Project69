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
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Extractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
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

    override val baseUrl = "https://tv3.nontondrama.my"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val extractor by lazy { Lk21Extractor(client, headers) }

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
        val userAgent = preferences.getString(PREF_USER_AGENT_KEY, PREF_USER_AGENT_DEFAULT)!!

        return super.headersBuilder().apply {
            add("User-Agent", userAgent)
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        }
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int) = GET("$baseUrl/populer/page/$page", headers)

    override fun popularAnimeSelector() = "div.gallery-grid article"

    override fun popularAnimeFromElement(element: Element) = SAnime.create().apply {
        setUrlWithoutDomain(element.selectFirst("figure a")!!.attr("href"))
        title = element.selectFirst("h3.poster-title")?.text()?.trim() ?: ""
        thumbnail_url = element.selectFirst("picture img")?.attr("src")

        ReportLog.log("LK21Series-Popular", "Parsed: $title", LogLevel.DEBUG)
    }

    override fun popularAnimeNextPageSelector() = "nav.pagination-wrapper ul.pagination li a[href*='/page/']"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest/page/$page", headers)

    override fun latestUpdatesSelector() = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // =============================== Search ===============================
    // Search disabled - Cloudflare protection

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) =
        throw UnsupportedOperationException("Search not supported")

    override fun searchAnimeSelector() = throw UnsupportedOperationException("Search not supported")

    override fun searchAnimeFromElement(element: Element) =
        throw UnsupportedOperationException("Search not supported")

    override fun searchAnimeNextPageSelector() = null

    override fun searchAnimeParse(response: Response) =
        throw UnsupportedOperationException("Search not supported")

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document) = SAnime.create().apply {
        title = document.selectFirst("div.movie-info h1")?.text()?.trim() ?: ""
        thumbnail_url = document.select("meta[property=og:image]").attr("content")
        genre = document.select("div.tag-list span a").joinToString(", ") { it.text() }

        // Status dari span.episode atau default completed
        status = if (document.selectFirst("span.episode.complete") != null) {
            SAnime.COMPLETED
        } else {
            SAnime.ONGOING
        }

        description = buildString {
            document.selectFirst("div.meta-info")?.text()?.let {
                append("Synopsis:\n$it\n\n")
            }

            document.selectFirst("span.year")?.text()?.let {
                append("Year: $it\n")
            }

            document.selectFirst("div.info-tag strong")?.text()?.let {
                append("Rating: $it\n")
            }

            document.selectFirst("a.yt-lightbox[href*=youtube]")?.attr("href")?.let {
                append("\nTrailer: $it")
            }
        }

        ReportLog.log("LK21Series-Detail", "Parsed: $title (Status: $status)", LogLevel.INFO)
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector() = "script#season-data"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodeList = mutableListOf<SEpisode>()

        ReportLog.log("LK21Series-Episodes", "Parsing from: ${response.request.url}", LogLevel.INFO)

        // Parse JSON dari script#season-data
        val seasonDataScript = document.selectFirst("script#season-data")?.data()

        if (seasonDataScript.isNullOrEmpty()) {
            ReportLog.log("LK21Series-Episodes", "No season data found", LogLevel.WARN)
            return emptyList()
        }

        try {
            // Parse JSON manually (format: {"1": [{episode_no, slug, s}, ...]})
            val json = org.json.JSONObject(seasonDataScript)
            val baseUrlPage = response.request.url.toString().substringBefore("?")
                .trimEnd('/').substringBeforeLast("/")

            json.keys().forEach { seasonKey ->
                val seasonArr = json.getJSONArray(seasonKey)
                for (i in 0 until seasonArr.length()) {
                    val ep = seasonArr.getJSONObject(i)
                    val slug = ep.getString("slug")
                    val episodeNo = ep.optInt("episode_no", i + 1)
                    val seasonNo = ep.optInt("s", seasonKey.toIntOrNull() ?: 1)

                    episodeList.add(
                        SEpisode.create().apply {
                            setUrlWithoutDomain("$baseUrlPage/$slug")
                            name = "Season $seasonNo Episode $episodeNo"
                            episode_number = episodeNo.toFloat()
                            date_upload = System.currentTimeMillis()
                        },
                    )
                }
            }

            ReportLog.log("LK21Series-Episodes", "Found ${episodeList.size} episodes", LogLevel.INFO)
        } catch (e: Exception) {
            ReportLog.reportError("LK21Series-Episodes", "JSON parse error: ${e.message}")
        }

        return episodeList.reversed()
    }

    override fun episodeFromElement(element: Element) = throw UnsupportedOperationException()

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()
        val pageUrl = response.request.url.toString()

        ReportLog.log("LK21Series-Video", "=== VIDEO PARSE START ===", LogLevel.INFO)
        ReportLog.log("LK21Series-Video", "Page URL: $pageUrl", LogLevel.INFO)

        // Ambil player list (dari LayarKacaProvider CloudStream logic)
        val playerItems = document.select("ul#player-list a[href]")
        ReportLog.log("LK21Series-Video", "Found ${playerItems.size} player links", LogLevel.INFO)

        data class PlayerEntry(val url: String, val name: String)
        val playerEntries = mutableListOf<PlayerEntry>()

        playerItems.forEach { element ->
            val serverName = element.text().trim()
            val href = element.attr("href").trim()

            if (href.isNotEmpty() && href != "#") {
                val fullUrl = when {
                    href.startsWith("http") -> href
                    href.startsWith("/") -> "$baseUrl$href"
                    else -> "$baseUrl/$href"
                }
                playerEntries.add(PlayerEntry(fullUrl, serverName))
                ReportLog.log("LK21Series-Video", "Player: $serverName → $fullUrl", LogLevel.DEBUG)
            }
        }

        // Fallback direct iframe
        if (playerEntries.isEmpty()) {
            ReportLog.log("LK21Series-Video", "No player-list, trying direct iframe", LogLevel.WARN)
            document.selectFirst("iframe[src]")?.let { iframe ->
                val iframeSrc = iframe.attr("src").trim()
                if (iframeSrc.isNotEmpty()) {
                    playerEntries.add(PlayerEntry(iframeSrc, "Direct Player"))
                    ReportLog.log("LK21Series-Video", "Direct iframe: $iframeSrc", LogLevel.INFO)
                }
            }
        }

        // Extract video dari setiap player (CloudStream logic)
        playerEntries.forEachIndexed { index, entry ->
            try {
                ReportLog.log("LK21Series-Video", "[$index] Following: ${entry.name} → ${entry.url}", LogLevel.DEBUG)

                // Follow link ke halaman intermediate
                val intermediateDoc = client.newCall(
                    GET(entry.url, headers.newBuilder().add("Referer", pageUrl).build()),
                ).execute().asJsoup()

                // Ambil iframe src
                val iframeSrc = intermediateDoc
                    .selectFirst("iframe[src]")
                    ?.attr("src")
                    ?.trim()
                    ?: ""

                ReportLog.log("LK21Series-Video", "[$index] Iframe src: $iframeSrc", LogLevel.DEBUG)

                if (iframeSrc.isEmpty()) {
                    // Kalau tidak ada iframe, coba resolve redirect
                    val resolvedUrl = resolveRedirect(entry.url)
                    ReportLog.log("LK21Series-Video", "[$index] No iframe, resolved: $resolvedUrl", LogLevel.WARN)
                    val videos = extractor.videosFromUrl(resolvedUrl, entry.name)
                    if (videos.isNotEmpty()) {
                        videoList.addAll(videos)
                    } else {
                        videoList.add(Video(resolvedUrl, "${entry.name} (Iframe)", resolvedUrl))
                    }
                    return@forEachIndexed
                }

                // Normalize iframe URL
                val finalIframeUrl = when {
                    iframeSrc.startsWith("//") -> "https:$iframeSrc"
                    iframeSrc.startsWith("http") -> iframeSrc
                    else -> iframeSrc
                }

                // Handle short.icu redirect (dari CloudStream)
                val resolvedUrl = if (finalIframeUrl.contains("short.icu")) {
                    resolveRedirect(finalIframeUrl)
                } else {
                    finalIframeUrl
                }

                ReportLog.log("LK21Series-Video", "[$index] Extracting from: $resolvedUrl", LogLevel.INFO)

                // Extract video
                val videos = extractor.videosFromUrl(resolvedUrl, entry.name)
                if (videos.isNotEmpty()) {
                    ReportLog.log("LK21Series-Video", "[$index] Found ${videos.size} video(s)", LogLevel.INFO)
                    videoList.addAll(videos)
                } else {
                    ReportLog.log("LK21Series-Video", "[$index] No videos, iframe fallback", LogLevel.WARN)
                    videoList.add(Video(resolvedUrl, "${entry.name} (Iframe)", resolvedUrl))
                }
            } catch (e: Exception) {
                ReportLog.reportError("LK21Series-Video", "[$index] Error: ${e.message}")
            }
        }

        ReportLog.log("LK21Series-Video", "=== TOTAL VIDEOS: ${videoList.size} ===", LogLevel.INFO)

        return videoList.ifEmpty {
            ReportLog.log("LK21Series-Video", "No videos, WebView fallback", LogLevel.WARN)
            listOf(Video(pageUrl, "Open in WebView", pageUrl))
        }
    }

    private fun resolveRedirect(url: String): String {
        return try {
            val response = client.newCall(
                GET(url, headers.newBuilder().add("Referer", baseUrl).build()),
            ).execute()
            val finalUrl = response.request.url.toString()
            ReportLog.log("LK21Series-Video", "Redirect: $url → $finalUrl", LogLevel.DEBUG)
            finalUrl
        } catch (e: Exception) {
            ReportLog.reportError("LK21Series-Video", "Redirect failed: ${e.message}")
            url
        }
    }

    override fun videoListSelector() = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList() = AnimeFilterList() // No filters

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        LK21SeriesPreferences.setupPreferences(screen, preferences)
    }

    companion object {
        private const val PREF_TIMEOUT_KEY = "network_timeout"
        private const val PREF_TIMEOUT_DEFAULT = "90"
        
        private const val PREF_USER_AGENT_KEY = "user_agent"
        private const val PREF_USER_AGENT_DEFAULT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
