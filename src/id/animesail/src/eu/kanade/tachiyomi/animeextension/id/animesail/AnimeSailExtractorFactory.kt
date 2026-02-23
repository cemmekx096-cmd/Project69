package eu.kanade.tachiyomi.animeextension.id.animesail

import android.content.SharedPreferences
import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.acefileextractor.AcefileExtractor
import eu.kanade.tachiyomi.lib.gofileextractor.GofileExtractor
import eu.kanade.tachiyomi.lib.krakenfilesextractor.KrakenfilesExtractor
import eu.kanade.tachiyomi.lib.mixdropextractor.MixDropExtractor
import eu.kanade.tachiyomi.lib.mp4uploadextractor.Mp4uploadExtractor
import eu.kanade.tachiyomi.lib.youruploadextractor.YourUploadExtractor
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * AnimeSail Extractor Factory
 *
 * Extracts video sources directly from episode page iframes and mirror options
 * with quality filtering and server whitelist support
 */
class AnimeSailExtractorFactory(
    private val client: OkHttpClient,
    private val headers: Headers,
    private val baseUrl: String,
    private val preferences: SharedPreferences,
) {

    private val tracker = FeatureTracker("ExtractorFactory")

    // Initialize extractors
    private val krakenfilesExtractor = KrakenfilesExtractor(client)
    private val gofileExtractor = GofileExtractor(client)
    private val acefileExtractor = AcefileExtractor(client)
    private val mp4uploadExtractor = Mp4uploadExtractor(client)
    private val mixdropExtractor = MixDropExtractor(client)
    private val youruploadExtractor = YourUploadExtractor(client)

    // Supported hosts whitelist
    private val supportedHosts = setOf(
        "acefile",
        "gofile",
        "mixdrop",
        "krakenfiles",
        "mp4upload",
        "yourupload"
    )

    /**
     * Extract videos from episode page
     *
     * Flow:
     * 1. Get default iframe from episode page
     * 2. Get mirror options from select dropdown
     * 3. Decode base64 mirror URLs
     * 4. Route each URL to appropriate extractor
     * 5. Filter by quality preference
     */
    fun extractVideos(response: Response): List<Video> {
        val perf = PerformanceTracker("ExtractVideos")
        perf.start()
        tracker.start()

        try {
            val document = response.asJsoup()
            val videos = mutableListOf<Video>()
            val processedUrls = mutableSetOf<String>()

            // 1. Get default iframe
            val defaultIframe = document.selectFirst("div.player-embed iframe[src]")?.attr("src")
            if (!defaultIframe.isNullOrBlank()) {
                tracker.debug("Default iframe: $defaultIframe")
                processVideoLink(defaultIframe, 0, videos, processedUrls)
            }

            // 2. Get mirror options from select dropdown
            val mirrors = document.select("select.mirror option[data-em]")
            tracker.debug("Found ${mirrors.size} mirror options")

            mirrors.forEachIndexed { index, option ->
                try {
                    val base64Data = option.attr("data-em")
                    if (base64Data.isNotBlank()) {
                        val decodedUrl = String(Base64.decode(base64Data, Base64.DEFAULT))
                        tracker.debug("[$index] Decoded mirror: $decodedUrl")

                        // Extract URL dari iframe tag jika ada
                        val actualUrl = if (decodedUrl.contains("<iframe")) {
                            decodedUrl.substringAfter("src=\"").substringBefore("\"")
                        } else {
                            decodedUrl
                        }

                        processVideoLink(actualUrl, index + 1, videos, processedUrls)
                    }
                } catch (e: Exception) {
                    tracker.error("[$index] Failed to decode mirror", e)
                }
            }

            // 3. Filter by quality preference
            val filteredVideos = filterVideosByQuality(videos)

            tracker.success("Extracted ${filteredVideos.size} videos (${videos.size} before quality filter)")
            perf.end()

            return filteredVideos.distinctBy { it.url }
        } catch (e: Exception) {
            tracker.error("Video extraction failed", e)
            perf.end()
            return emptyList()
        }
    }

    /**
     * Process single video URL and route to appropriate extractor
     */
    private fun processVideoLink(
        url: String,
        index: Int,
        videos: MutableList<Video>,
        processedUrls: MutableSet<String>,
    ) {
        if (url.isBlank() || url in processedUrls) {
            return
        }

        // Check whitelist preference
        val serverPref = AnimeSailPreferences.getPreferredServer(preferences)
        if (serverPref == "supported" && !isSupportedHost(url)) {
            tracker.debug("[$index] Skipped (whitelist enabled): $url")
            return
        }

        processedUrls.add(url)
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

                "mp4upload" in url.lowercase() -> {
                    tracker.debug("[$index] Using Mp4upload extractor")
                    val extractedVideos = mp4uploadExtractor.videosFromUrl(url, headers)
                    videos.addAll(extractedVideos)
                    tracker.debug("[$index] Extracted ${extractedVideos.size} videos from Mp4upload")
                }

                "mixdrop" in url.lowercase() -> {
                    tracker.debug("[$index] Using Mixdrop extractor")
                    val extractedVideos = mixdropExtractor.videosFromUrl(url)
                    videos.addAll(extractedVideos)
                    tracker.debug("[$index] Extracted ${extractedVideos.size} videos from Mixdrop")
                }

                "yourupload" in url.lowercase() -> {
                    tracker.debug("[$index] Using YourUpload extractor")
                    val extractedVideos = youruploadExtractor.videoFromUrl(url, headers)
                    videos.addAll(extractedVideos)
                    tracker.debug("[$index] Extracted ${extractedVideos.size} videos from YourUpload")
                }

                // Fallback untuk extractor yang belum ada
                "hexupload" in url.lowercase() -> {
                    tracker.warn("[$index] Hexupload extractor not available yet")
                    videos.add(Video(url, "Hexupload (Manual)", url))
                }

                "aghanim.xyz" in url.lowercase() -> {
                    tracker.warn("[$index] Lokal extractor not available yet")
                    videos.add(Video(url, "Lokal (Manual)", url))
                }

                else -> {
                    tracker.warn("[$index] Unknown host: $url")
                }
            }
        } catch (e: Exception) {
            tracker.error("[$index] Failed to extract from $url", e)
        }
    }

    /**
     * Check if URL is from supported host
     */
    private fun isSupportedHost(url: String): Boolean {
        return supportedHosts.any { url.lowercase().contains(it) }
    }

    /**
     * Filter videos by quality preference
     */
    private fun filterVideosByQuality(videos: List<Video>): List<Video> {
        val qualityPref = AnimeSailPreferences.getPreferredQuality(preferences)
        
        if (qualityPref == "auto") {
            // Return all videos, let user choose
            return videos
        }

        // Filter by specific quality
        val filtered = videos.filter { video ->
            video.quality.contains(qualityPref, ignoreCase = true)
        }

        // If no videos match preferred quality, return all (fallback)
        return if (filtered.isNotEmpty()) {
            tracker.debug("Filtered to ${filtered.size} videos with quality: $qualityPref")
            filtered
        } else {
            tracker.warn("No videos found with quality $qualityPref, returning all")
            videos
        }
    }
}
