package eu.kanade.tachiyomi.animeextension.id.animesail

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.cloudflareinterceptor.CloudflareInterceptor
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

/**
 * AnimeSail Extension for Aniyomi
 *
 * Indonesian anime streaming site with multiple video sources.
 * Ported from CloudStream3.
 *
 * Features:
 * - Latest episodes
 * - Anime & Donghua sections
 * - Search functionality
 * - Multiple video servers (Krakenfiles, Gofile, Acefile)
 * - Episode navigation
 */
class AnimeSail : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "AnimeSail"

    override val lang = "id"

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val baseUrl: String
        get() = AnimeSailPreferences.getBaseUrl(preferences)

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .addInterceptor(CloudflareInterceptor(network.cloudflareClient))
            .build()
    }

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().apply {
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            add("Cookie", "_as_ipin_tz=Asia/Jakarta; _as_ipin_lc=en-US; _as_ipin_ct=ID")
            add("Referer", baseUrl)
        }
    }

    // ============================== Popular (Episode Terbaru) ==============================

    override fun popularAnimeRequest(page: Int): Request {
        val tracker = FeatureTracker("PopularAnime")
        tracker.start()
        tracker.debug("Page: $page")

        val url = when (page) {
            1 -> baseUrl
            else -> "$baseUrl/page/$page"
        }

        tracker.debug("URL: $url")
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "div.listupd article"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            // Get title (remove "Episode" suffix)
            val titleText = element.selectFirst("a")?.attr("title") ?: ""
            title = titleText.substringBefore("Episode").trim()

            // Get URL (navigate to anime page, not episode)
            val href = element.selectFirst("a")?.attr("href") ?: ""
            setUrlWithoutDomain(getProperAnimeLink(href))

            // Get thumbnail
            thumbnail_url = element.selectFirst("div.limit img")?.attr("src")
        }
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a.next"

    // ============================== Latest (Anime Terbaru) ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        val tracker = FeatureTracker("LatestAnime")
        tracker.start()
        tracker.debug("Page: $page")

        val url = when (page) {
            1 -> "$baseUrl/rilisan-anime-terbaru"
            else -> "$baseUrl/rilisan-anime-terbaru/page/$page"
        }

        tracker.debug("URL: $url")
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Search ==============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val tracker = FeatureTracker("SearchAnime")
        tracker.start()

        return if (query.isNotBlank()) {
            tracker.debug("Query: $query")
            val searchUrl = "$baseUrl/page/$page/?s=$query"
            tracker.debug("URL: $searchUrl")
            GET(searchUrl, headers)
        } else {
            tracker.debug("Using filters")
            // TODO: Implement filter-based search if needed
            // For now, fallback to popular
            popularAnimeRequest(page)
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // ============================== Anime Details ==============================

    override fun animeDetailsParse(document: Document): SAnime {
        val tracker = FeatureTracker("AnimeDetails")
        tracker.start()

        return SAnime.create().apply {
            // Title
            title = document.selectFirst("h1.entry-title")?.text()
                ?.substringBefore("Subtitle")
                ?.trim() ?: ""

            tracker.debug("Title: $title")

            // Thumbnail
            thumbnail_url = document.selectFirst("div.entry-content.serial-info img")?.attr("src")

            // Description
            description = document.selectFirst("div.entry-content.serial-info p:nth-child(2)")
                ?.text()?.trim()

            // Tags/Genre
            genre = document.select("table tr:has(th:matchesOwn(^\\s*Genre:)) td")
                .text()
                .trim()
                .split(", ")
                .joinToString(", ")

            status = SAnime.COMPLETED

            tracker.success("Parsed anime details")
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val tracker = FeatureTracker("EpisodeList")
        val perf = PerformanceTracker("ParseEpisodes")

        perf.start()
        tracker.start()

        val document = response.asJsoup()

        // Parse episode list
        val episodes = document.select("ul.daftar li").mapNotNull { element ->
            try {
                val episodeLink = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val episodeText = element.selectFirst("a")?.text() ?: return@mapNotNull null

                // Extract episode number
                val episodeNumber = episodeText
                    .substringAfter("Episode")
                    .substringBefore("Subtitle")
                    .trim()
                    .toIntOrNull()

                SEpisode.create().apply {
                    setUrlWithoutDomain(fixUrl(episodeLink, baseUrl))
                    name = "Episode $episodeNumber"
                    episode_number = episodeNumber?.toFloat() ?: 0f
                }
            } catch (e: Exception) {
                tracker.error("Failed to parse episode", e)
                null
            }
        }.reversed() // Reverse to show latest first

        tracker.success("Found ${episodes.size} episodes")
        perf.end()

        return episodes
    }

    override fun episodeListSelector(): String = throw Exception("Not used")

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("Not used")

    // ============================== Video Links ==============================

    override fun videoListParse(response: Response): List<Video> {
        val factory = AnimeSailExtractorFactory(client, headers, baseUrl)
        return factory.extractVideos(response)
    }

    override fun videoListSelector(): String = throw Exception("Not used")

    override fun videoFromElement(element: Element): Video = throw Exception("Not used")

    override fun videoUrlParse(document: Document): String = throw Exception("Not used")

    // ============================== Filters ==============================

    override fun getFilterList(): AnimeFilterList {
        return AnimeSailFilters.getFilterList()
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        AnimeSailPreferences.setupPreferenceScreen(screen, preferences)
    }

    // ============================== Utilities ==============================

    /**
     * Navigate from episode page to anime page
     * Episode URL: /episode-1/
     * Anime URL: /anime-title/
     */
    private fun getProperAnimeLink(episodeUrl: String): String {
        try {
            // Fetch episode page to get anime link
            val response = client.newCall(GET(fixUrl(episodeUrl, baseUrl), headers)).execute()
            val document = response.asJsoup()

            // Find anime link in breadcrumb
            val animeLink = document.selectFirst("div.breadcrumb span:nth-child(3) a")?.attr("href")

            if (!animeLink.isNullOrBlank()) {
                return animeLink.removePrefix(baseUrl)
            }
        } catch (e: Exception) {
            ReportLog.reportError("GetAnimeLink", "Failed to get anime link", e)
        }

        // Fallback: return original URL
        return episodeUrl.removePrefix(baseUrl)
    }
}
