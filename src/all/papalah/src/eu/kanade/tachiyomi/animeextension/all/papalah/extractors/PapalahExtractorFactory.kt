package eu.kanade.tachiyomi.animeextension.all.papalah.extractors

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient
import kotlin.math.max
import kotlin.math.min

class PapalahExtractorFactory(
    private val client: OkHttpClient,
    private val headers: Headers,
) {

    private val TAG = "PapalahExtractor"

    companion object {
        // Semua CDN domains yang digunakan
        private val VIDEO_CDN_DOMAINS = listOf(
            "media.aiailah.com",
            "media.sslah.com",
            "media.aalah.me:8443",
            "media.papalah.com",
            "media.aalah.me",
        )

        // Video file extensions yang didukung
        private val VIDEO_EXTENSIONS = listOf(".mp4", ".m3u8")
    }

    fun extractFromHtml(html: String, referer: String = ""): List<Video> {
        val videos = mutableListOf<Video>()

        Log.d(TAG, "=== START EXTRACTION ===")
        Log.d(TAG, "HTML length: ${html.length} chars")
        Log.d(TAG, "Referer: $referer")

        // METHOD 1: Video tag langsung
        Log.d(TAG, "🔍 Searching for <video src> tags...")
        val videoSrcPattern = """<video[^>]+src\s*=\s*["']([^"']+\.mp4)["']"""
        Regex(videoSrcPattern, RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEachIndexed { index, match ->
                val url = match.groupValues[1].trim()
                Log.d(TAG, "  ✅ Found video #${index + 1}: $url")
                if (isValidVideoUrl(url)) {
                    videos.add(createVideo(url, "Direct Video"))
                }
            }

        // METHOD 2: Source tags
        Log.d(TAG, "🔍 Searching for <source> tags...")
        val sourcePattern = """<source[^>]+src\s*=\s*["']([^"']+\.mp4)["'][^>]+type\s*=\s*["']video/mp4["']"""
        Regex(sourcePattern, RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEachIndexed { index, match ->
                val url = match.groupValues[1].trim()
                Log.d(TAG, "  ✅ Found source #${index + 1}: $url")
                if (isValidVideoUrl(url)) {
                    videos.add(createVideo(url, "Source Tag"))
                }
            }

        // METHOD 3: Fallback - cari semua URL mp4
        if (videos.isEmpty()) {
            Log.d(TAG, "⚠️ No videos found with patterns, trying fallback...")

            // Pattern untuk semua CDN domains
            val cdnPatterns = VIDEO_CDN_DOMAINS.joinToString("|") { it.replace(".", "\\.") }
            val fallbackPattern = """(https?://(?:$cdnPatterns)/[^\s"'<>]+\.mp4)"""

            Regex(fallbackPattern, RegexOption.IGNORE_CASE)
                .findAll(html)
                .forEachIndexed { index, match ->
                    val url = match.groupValues[1].trim()
                    if (!videos.any { it.url == url }) {
                        Log.d(TAG, "  ✅ Found via fallback #${index + 1}: $url")
                        if (isValidVideoUrl(url)) {
                            videos.add(createVideo(url, "Fallback"))
                        }
                    }
                }
        }

        // METHOD 4: Debug - cari semua .mp4 jika masih kosong
        if (videos.isEmpty()) {
            Log.e(TAG, "❌ NO VIDEOS FOUND!")

            // Simpan HTML snippet untuk debugging
            val videoSection = extractVideoSection(html)
            Log.d(TAG, "📄 HTML Video Section (500 chars):")
            Log.d(TAG, videoSection)

            // Cari semua .mp4 di HTML
            Log.d(TAG, "🔍 Trying final fallback - all .mp4 URLs...")
            val allMp4Pattern = """https?://[^\s"'<>]+\.mp4"""
            Regex(allMp4Pattern, RegexOption.IGNORE_CASE)
                .findAll(html)
                .forEachIndexed { index, match ->
                    val url = match.value.trim()
                    Log.d(TAG, "  🟡 Found mp4 #${index + 1}: $url")
                    if (isValidVideoUrl(url)) {
                        Log.d(TAG, "  ✅ Valid video: $url")
                        videos.add(createVideo(url, "Final Fallback"))
                    }
                }
        }

        val uniqueVideos = videos.distinctBy { it.url }

        Log.d(TAG, "=== EXTRACTION COMPLETE ===")
        Log.d(TAG, "📊 Total videos found: ${videos.size}")
        Log.d(TAG, "📊 Unique videos: ${uniqueVideos.size}")

        uniqueVideos.forEachIndexed { index, video ->
            Log.d(TAG, "  ${index + 1}. ${video.quality} - ${video.url}")
        }

        return uniqueVideos
    }

    private fun extractVideoSection(html: String): String {
        // Cari section dengan video player
        val videoIndex = html.indexOf("<video")
        if (videoIndex == -1) return "No <video> tag found"

        val start = max(0, videoIndex - 100)
        val end = min(html.length, videoIndex + 400)
        return html.substring(start, end)
    }

    private fun isValidVideoUrl(url: String): Boolean {
        if (url.isBlank()) return false

        // Harus berakhiran .mp4
        if (!url.lowercase().endsWith(".mp4")) return false

        // Harus dari salah satu CDN domain yang dikenal
        val isValidDomain = VIDEO_CDN_DOMAINS.any { domain ->
            url.contains(domain)
        }

        if (!isValidDomain) {
            Log.d(TAG, "❌ Invalid domain for URL: $url")
        }

        return isValidDomain
    }

    private fun createVideo(url: String, source: String): Video {
        // Coba detect quality dari URL (jika ada pattern)
        val quality = detectQualityFromUrl(url)
        val label = "Papalah" + if (quality.isNotEmpty()) " - $quality" else ""

        return Video(url, label, url, headers)
    }

    private fun detectQualityFromUrl(url: String): String {
        val lowerUrl = url.lowercase()

        return when {
            lowerUrl.contains("4k") || lowerUrl.contains("2160p") -> "4K"
            lowerUrl.contains("1080p") || lowerUrl.contains("fullhd") -> "1080p"
            lowerUrl.contains("720p") || lowerUrl.contains("hd") -> "720p"
            lowerUrl.contains("480p") || lowerUrl.contains("sd") -> "480p"
            lowerUrl.contains("360p") || lowerUrl.contains("low") -> "360p"
            lowerUrl.contains("240p") -> "240p"
            lowerUrl.contains("144p") -> "144p"
            else -> {
                // Try to guess from file path
                when {
                    lowerUrl.contains("/hd/") -> "HD"
                    lowerUrl.contains("/sd/") -> "SD"
                    lowerUrl.contains("/high/") -> "High"
                    lowerUrl.contains("/medium/") -> "Medium"
                    lowerUrl.contains("/low/") -> "Low"
                    else -> "HD" // Default ke HD karena kebanyakan video situs ini HD
                }
            }
        }
    }
}
