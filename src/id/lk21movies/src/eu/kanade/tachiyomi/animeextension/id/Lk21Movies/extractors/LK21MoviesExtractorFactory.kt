package eu.kanade.tachiyomi.animeextension.id.lk21movies.extractors

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

class LK21MoviesExtractorFactory(
    private val client: OkHttpClient,
    private val headers: Headers,
) {

    private val TAG = "LK21MoviesExtractor"

    companion object {
        // Known video servers/embedders
        private val KNOWN_SERVERS = listOf(
            "p2p", "turbovip", "cast", "hydrax",
            "gdriveplayer", "streamtape", "mixdrop",
            "doodstream", "upstream", "fembed",
        )
    }

    /**
     * Extract videos dari server URL
     */
    fun extractFromServer(serverUrl: String, serverName: String, referer: String): List<Video> {
        val videos = mutableListOf<Video>()

        Log.d(TAG, "=== EXTRACTING FROM SERVER ===")
        Log.d(TAG, "Server: $serverName")
        Log.d(TAG, "URL: $serverUrl")
        Log.d(TAG, "Referer: $referer")

        try {
            // Fetch HTML dari server
            val response = client.newCall(
                okhttp3.Request.Builder()
                    .url(serverUrl)
                    .header("Referer", referer)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build(),
            ).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to fetch server: ${response.code}")
                return emptyList()
            }

            val html = response.body.string()
            val document = Jsoup.parse(html)

            // METHOD 1: Extract dari video tag
            document.select("video source[src]").forEach { source ->
                val videoUrl = source.attr("src")
                if (isValidVideoUrl(videoUrl)) {
                    val quality = detectQualityFromUrl(videoUrl) ?: "Unknown"
                    videos.add(createVideo(videoUrl, "$serverName - $quality"))
                    Log.d(TAG, "✅ Found from <source>: $videoUrl")
                }
            }

            // METHOD 2: Extract dari atribut video src
            document.select("video[src]").forEach { video ->
                val videoUrl = video.attr("src")
                if (isValidVideoUrl(videoUrl)) {
                    val quality = detectQualityFromUrl(videoUrl) ?: "Unknown"
                    videos.add(createVideo(videoUrl, "$serverName - $quality"))
                    Log.d(TAG, "✅ Found from <video src>: $videoUrl")
                }
            }

            // METHOD 3: Extract dari iframe (nested embed)
            if (videos.isEmpty()) {
                val iframe = document.selectFirst("iframe[src]")?.attr("src")
                if (!iframe.isNullOrEmpty() && iframe != serverUrl) {
                    Log.d(TAG, "🔄 Found nested iframe: $iframe")
                    // Recursive call untuk nested iframe
                    videos.addAll(extractFromServer(iframe, "$serverName (Nested)", referer))
                }
            }

            // METHOD 4: Extract dari JavaScript patterns
            if (videos.isEmpty()) {
                videos.addAll(extractFromJavaScript(html, serverName))
            }

            // METHOD 5: Fallback - cari semua .mp4 atau .m3u8 URLs
            if (videos.isEmpty()) {
                val videoPattern = """(https?://[^\s"'<>]+\.(?:mp4|m3u8))"""
                Regex(videoPattern, RegexOption.IGNORE_CASE)
                    .findAll(html)
                    .forEach { match ->
                        val videoUrl = match.groupValues[1]
                        if (isValidVideoUrl(videoUrl)) {
                            val quality = detectQualityFromUrl(videoUrl) ?: "Unknown"
                            videos.add(createVideo(videoUrl, "$serverName - $quality (Fallback)"))
                            Log.d(TAG, "✅ Found from regex: $videoUrl")
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error extracting from $serverName: ${e.message}")
            e.printStackTrace()
        }

        Log.d(TAG, "📊 Total videos from $serverName: ${videos.size}")
        return videos.distinctBy { it.url }
    }

    /**
     * Extract video URLs dari JavaScript code
     */
    private fun extractFromJavaScript(html: String, serverName: String): List<Video> {
        val videos = mutableListOf<Video>()

        try {
            // Pattern 1: file: "url.mp4"
            val filePattern = """file\s*:\s*["']([^"']+\.(?:mp4|m3u8))["']"""
            Regex(filePattern, RegexOption.IGNORE_CASE)
                .findAll(html)
                .forEach { match ->
                    val videoUrl = match.groupValues[1]
                    if (isValidVideoUrl(videoUrl)) {
                        val quality = detectQualityFromUrl(videoUrl) ?: "Unknown"
                        videos.add(createVideo(videoUrl, "$serverName - $quality (JS)"))
                        Log.d(TAG, "✅ Found from JS file: $videoUrl")
                    }
                }

            // Pattern 2: sources: [{file: "url.mp4"}]
            val sourcesPattern = """sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)["']"""
            Regex(sourcesPattern, RegexOption.IGNORE_CASE)
                .findAll(html)
                .forEach { match ->
                    val videoUrl = match.groupValues[1]
                    if (isValidVideoUrl(videoUrl)) {
                        val quality = detectQualityFromUrl(videoUrl) ?: "Unknown"
                        videos.add(createVideo(videoUrl, "$serverName - $quality (JS Sources)"))
                        Log.d(TAG, "✅ Found from JS sources: $videoUrl")
                    }
                }

            // Pattern 3: "url": "https://..."
            val urlPattern = """"url"\s*:\s*"([^"]+\.(?:mp4|m3u8))""""
            Regex(urlPattern, RegexOption.IGNORE_CASE)
                .findAll(html)
                .forEach { match ->
                    val videoUrl = match.groupValues[1]
                    if (isValidVideoUrl(videoUrl)) {
                        val quality = detectQualityFromUrl(videoUrl) ?: "Unknown"
                        videos.add(createVideo(videoUrl, "$serverName - $quality (JSON)"))
                        Log.d(TAG, "✅ Found from JSON url: $videoUrl")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting from JavaScript: ${e.message}")
        }

        return videos
    }

    /**
     * Validate video URL
     */
    private fun isValidVideoUrl(url: String): Boolean {
        if (url.isBlank()) return false

        // Harus https
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false

        // Harus berakhiran video extension atau m3u8
        val lowerUrl = url.lowercase()
        return lowerUrl.endsWith(".mp4") ||
            lowerUrl.endsWith(".m3u8") ||
            lowerUrl.contains(".mp4?") ||
            lowerUrl.contains(".m3u8?")
    }

    /**
     * Detect quality dari URL atau nama file
     */
    private fun detectQualityFromUrl(url: String): String? {
        val lowerUrl = url.lowercase()

        return when {
            lowerUrl.contains("4k") || lowerUrl.contains("2160p") -> "4K"
            lowerUrl.contains("1080p") || lowerUrl.contains("fullhd") || lowerUrl.contains("fhd") -> "1080p"
            lowerUrl.contains("720p") || lowerUrl.contains("hd") -> "720p"
            lowerUrl.contains("480p") || lowerUrl.contains("sd") -> "480p"
            lowerUrl.contains("360p") -> "360p"
            lowerUrl.contains("240p") -> "240p"
            lowerUrl.contains("144p") -> "144p"
            else -> {
                // Coba detect dari path
                when {
                    lowerUrl.contains("/hd/") -> "HD"
                    lowerUrl.contains("/sd/") -> "SD"
                    lowerUrl.contains("/high/") -> "High"
                    lowerUrl.contains("/medium/") -> "Medium"
                    lowerUrl.contains("/low/") -> "Low"
                    lowerUrl.endsWith(".m3u8") -> "HLS"
                    else -> null
                }
            }
        }
    }

    /**
     * Create Video object
     */
    private fun createVideo(url: String, quality: String): Video {
        val videoHeaders = headers.newBuilder()
            .add("Referer", url.substringBefore("/", ""))
            .add("Accept", "*/*")
            .build()

        return Video(url, quality, url, headers = videoHeaders)
    }
}
