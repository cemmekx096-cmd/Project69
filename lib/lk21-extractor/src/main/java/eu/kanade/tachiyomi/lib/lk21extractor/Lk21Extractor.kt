package eu.kanade.tachiyomi.lib.lk21extractor

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

/**
 * LK21 Video Extractor - Simplified & Reliable
 * Fokus pada quality daripada quantity server
 */
class Lk21Extractor(
    private val client: OkHttpClient,
    private val headers: Headers
) {

    companion object {
        private const val TAG = "Lk21Extractor"
    }

    /**
     * Extract videos dari iframe URL
     * @param iframeUrl URL iframe player
     * @param serverName Label server untuk quality string
     * @return List of Video objects
     */
    fun videosFromUrl(iframeUrl: String, serverName: String = "Player"): List<Video> {
        if (iframeUrl.isBlank()) {
            Log.w(TAG, "Empty iframe URL")
            return emptyList()
        }

        return try {
            Log.d(TAG, "Extracting from: $iframeUrl")

            val response = client.newCall(GET(iframeUrl, headers)).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} for $iframeUrl")
                return emptyList()
            }

            val document = response.asJsoup()
            val videoList = mutableListOf<Video>()

            // Method 1: Direct video tags
            document.select("video source[src], video[src]").forEach { element ->
                val videoUrl = element.attr("src").ifBlank { 
                    element.attr("data-src") 
                }

                if (videoUrl.isNotBlank() && videoUrl.startsWith("http")) {
                    val quality = extractQuality(videoUrl, serverName)
                    videoList.add(Video(videoUrl, quality, videoUrl, headers))
                    Log.d(TAG, "Found video tag: $quality")
                }
            }

            // Method 2: JavaScript sources (common patterns)
            document.select("script").forEach { script ->
                val scriptText = script.html()

                // Pattern: file: "https://..."
                extractFromPattern(
                    scriptText,
                    """file:\s*["']([^"']+)["']""".toRegex(),
                    serverName,
                    videoList
                )

                // Pattern: src: "https://..."
                extractFromPattern(
                    scriptText,
                    """src:\s*["']([^"']+)["']""".toRegex(),
                    serverName,
                    videoList
                )

                // Pattern: sources: [{file: "..."}]
                extractFromPattern(
                    scriptText,
                    """sources:\s*\[?\{[^}]*file:\s*["']([^"']+)["']""".toRegex(),
                    serverName,
                    videoList
                )

                // Pattern: Direct .m3u8 or .mp4 URLs
                extractFromPattern(
                    scriptText,
                    """https?://[^\s"'<>]+\.(?:m3u8|mp4)""".toRegex(),
                    serverName,
                    videoList
                )
            }

            // Method 3: Nested iframes (recursive, 1 level deep only)
            if (videoList.isEmpty()) {
                document.select("iframe[src]").forEach { iframe ->
                    val nestedUrl = iframe.attr("src")
                    if (nestedUrl.isNotBlank() && 
                        nestedUrl.startsWith("http") && 
                        nestedUrl != iframeUrl) {

                        Log.d(TAG, "Trying nested iframe: $nestedUrl")
                        videoList.addAll(videosFromUrl(nestedUrl, "$serverName (Nested)"))
                    }
                }
            }

            Log.d(TAG, "Extracted ${videoList.size} videos from $serverName")
            videoList.distinctBy { it.url }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting from $iframeUrl", e)
            emptyList()
        }
    }

    /**
     * Helper: Extract video URLs from regex pattern
     */
    private fun extractFromPattern(
        scriptText: String,
        pattern: Regex,
        serverName: String,
        videoList: MutableList<Video>
    ) {
        pattern.findAll(scriptText).forEach { match ->
            val videoUrl = match.groupValues.getOrNull(1) ?: match.value

            if (videoUrl.isNotBlank() && 
                videoUrl.startsWith("http") &&
                (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))) {

                val quality = extractQuality(videoUrl, serverName)
                videoList.add(Video(videoUrl, quality, videoUrl, headers))
            }
        }
    }

    /**
     * Extract quality dari URL atau filename
     * Format: "ServerName - Quality"
     */
    private fun extractQuality(url: String, serverName: String): String {
        val quality = when {
            url.contains("1080", ignoreCase = true) -> "1080p"
            url.contains("720", ignoreCase = true) -> "720p"
            url.contains("480", ignoreCase = true) -> "480p"
            url.contains("360", ignoreCase = true) -> "360p"
            url.contains(".m3u8", ignoreCase = true) -> "HLS"
            url.contains(".mp4", ignoreCase = true) -> "MP4"
            else -> "Default"
        }

        return "$serverName - $quality"
    }
}
