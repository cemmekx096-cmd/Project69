package eu.kanade.tachiyomi.animeextension.id.animesail

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.acefileextractor.AcefileExtractor
import eu.kanade.tachiyomi.lib.gofileextractor.GofileExtractor
import eu.kanade.tachiyomi.lib.krakenfilesextractor.KrakenfilesExtractor
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document

/**
 * AnimeSail Extractor Factory
 *
 * Routes video extraction to appropriate library based on URL:
 * - Krakenfiles → KrakenfilesExtractor
 * - Gofile → GofileExtractor
 * - Acefile → AcefileExtractor
 *
 * Handles navigation from episode page → download page → video URLs
 */
class AnimeSailExtractorFactory(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val baseUrl: String,
) {

    private val tracker = FeatureTracker("ExtractorFactory")

    // Initialize extractors
    private val krakenfilesExtractor = KrakenfilesExtractor(client)
    private val gofileExtractor = GofileExtractor(client)
    private val acefileExtractor = AcefileExtractor(client)

    /**
     * Extract videos from episode page
     *
     * Flow:
     * 1. Parse episode page
     * 2. Navigate to download page
     * 3. Extract video links from download page
     * 4. Route each link to appropriate extractor
     */
    fun extractVideos(response: Response): List<Video> {
        val perf = PerformanceTracker("ExtractVideos")
        perf.start()
        tracker.start()

        try {
            val document = response.asJsoup()

            // Get download page URL
            tracker.debug("Navigating to download page...")
            val downloadPageUrl = getDownloadPageUrl(document)

            if (downloadPageUrl.isEmpty()) {
                tracker.error("Download page URL not found")
                return emptyList()
            }

            tracker.debug("Download page: $downloadPageUrl")

            // Fetch download page
            val downloadPageResponse = client.newCall(
                okhttp3.Request.Builder()
                    .url(downloadPageUrl)
                    .headers(headers)
                    .build(),
            ).execute()

            if (!downloadPageResponse.isSuccessful) {
                tracker.error("Failed to fetch download page: ${downloadPageResponse.code}")
                return emptyList()
            }

            val downloadPage = downloadPageResponse.asJsoup()

            // Extract video links
            val videos = extractVideosFromDownloadPage(downloadPage)

            tracker.success("Extracted ${videos.size} videos")
            perf.end()

            return videos.distinctBy { it.url }
        } catch (e: Exception) {
            tracker.error("Video extraction failed", e)
            perf.end()
            return emptyList()
        }
    }

    /**
     * Get download page URL from episode page
     */
    private fun getDownloadPageUrl(document: Document): String {
        // Find download link (based on CloudStream pattern)
        val downloadLink = document.selectFirst("center:has(a.singledl) a")?.attr("href")

        if (downloadLink.isNullOrBlank()) {
            tracker.warn("Download link not found with primary selector")

            // Try alternative selectors
            val altLink = document.selectFirst("a.singledl")?.attr("href")
                ?: document.selectFirst("a[href*=download]")?.attr("href")

            if (altLink.isNullOrBlank()) {
                return ""
            }

            return fixUrl(altLink, baseUrl)
        }

        return fixUrl(downloadLink, baseUrl)
    }

    /**
     * Extract videos from download page
     */
    private fun extractVideosFromDownloadPage(document: Document): List<Video> {
        val videos = mutableListOf<Video>()

        // Find all video links in table
        val videoLinks = document.select("table a")
        tracker.debug("Found ${videoLinks.size} video links")

        if (videoLinks.isEmpty()) {
            tracker.warn("No video links found in download page")
            // Try alternative selector
            val altLinks = document.select("a[data-href], a[href*=gofile], a[href*=acefile], a[href*=krakenfiles]")
            tracker.debug("Alternative selector found ${altLinks.size} links")

            altLinks.forEachIndexed { index, link ->
                processVideoLink(link.attr("data-href").ifEmpty { link.attr("href") }, index, videos)
            }
        } else {
            videoLinks.forEachIndexed { index, link ->
                val url = link.attr("data-href").ifEmpty { link.attr("href") }
                processVideoLink(url, index, videos)
            }
        }

        return videos
    }

    /**
     * Process single video link and route to appropriate extractor
     */
    private fun processVideoLink(url: String, index: Int, videos: MutableList<Video>) {
        if (url.isBlank()) {
            tracker.warn("[$index] Empty URL, skipping")
            return
        }

        tracker.debug("[$index] Processing: $url")

        try {
            when {
                "krakenfiles" in url.lowercase() -> {
                    tracker.debug("[$index] Using Krakenfiles extractor")
                    val extractedVideos = krakenfilesExtractor.videosFromUrl(url, headers)
                    videos.addAll(extractedVideos)
                    tracker.debug("[$index] Extracted ${extractedVideos.size} videos from Krakenfiles")
                }

                "gofile" in url.lowercase() -> {
                    tracker.debug("[$index] Using Gofile extractor")
                    val extractedVideos = gofileExtractor.videosFromUrl(url, headers)
                    videos.addAll(extractedVideos)
                    tracker.debug("[$index] Extracted ${extractedVideos.size} videos from Gofile")
                }

                "acefile" in url.lowercase() -> {
                    tracker.debug("[$index] Using Acefile extractor")
                    val extractedVideos = acefileExtractor.videosFromUrl(url, headers)
                    videos.addAll(extractedVideos)
                    tracker.debug("[$index] Extracted ${extractedVideos.size} videos from Acefile")
                }

                else -> {
                    tracker.warn("[$index] Unknown host: $url")
                }
            }
        } catch (e: Exception) {
            tracker.error("[$index] Failed to extract from $url", e)
        }
    }
}
