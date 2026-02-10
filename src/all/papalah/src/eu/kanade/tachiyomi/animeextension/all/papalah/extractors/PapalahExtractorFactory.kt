package eu.kanade.tachiyomi.animeextension.all.papalah.extractors

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

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
            "media.aalah.me"
        )
    }

    fun extractFromHtml(html: String, referer: String = ""): List<Video> {
        val videos = mutableListOf<Video>()

        Log.d(TAG, "🔍 Starting video extraction...")

        // PATTERN 1: <video> tag dengan src attribute
        // Contoh: <video ... src="https://media.aiailah.com/videos/d/f/df7bfa09b43d8ac68d9e89e6a63e4176.mp4">
        val videoSrcPattern = """<video[^>]+src\s*=\s*["']([^"']+\.mp4)["']"""
        Regex(videoSrcPattern, RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                val url = match.groupValues[1].trim()
                if (isValidVideoUrl(url)) {
                    Log.d(TAG, "✅ Found video src: $url")
                    videos.add(createVideo(url, "Direct Video"))
                }
            }

        // PATTERN 2: <source> tag dengan src attribute  
        // Contoh: <source src="https://media.aiailah.com/videos/d/f/df7bfa09b43d8ac68d9e89e6a63e4176.mp4" type="video/mp4">
        val sourcePattern = """<source[^>]+src\s*=\s*["']([^"']+\.mp4)["'][^>]+type\s*=\s*["']video/mp4["']"""
        Regex(sourcePattern, RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                val url = match.groupValues[1].trim()
                if (isValidVideoUrl(url)) {
                    Log.d(TAG, "✅ Found source: $url")
                    videos.add(createVideo(url, "Source Tag"))
                }
            }

        // PATTERN 3: Fallback - cari semua URL mp4 dari CDN domains
        if (videos.isEmpty()) {
            Log.d(TAG, "⚠️ No videos found with patterns, trying fallback...")

            // Buat pattern untuk semua CDN domains
            val cdnPatterns = VIDEO_CDN_DOMAINS.joinToString("|") { it.replace(".", "\\.") }
            val fallbackPattern = """(https?://(?:$cdnPatterns)/[^\s"'<>]+\.mp4)"""

            Regex(fallbackPattern, RegexOption.IGNORE_CASE)
                .findAll(html)
                .forEach { match ->
                    val url = match.groupValues[1].trim()
                    if (!videos.any { it.url == url }) {
                        Log.d(TAG, "✅ Found via fallback: $url")
                        videos.add(createVideo(url, "Fallback"))
                    }
                }
        }

        // Remove duplicates
        val uniqueVideos = videos.distinctBy { it.url }

        Log.d(TAG, "📊 Total unique videos found: ${uniqueVideos.size}")
        uniqueVideos.forEachIndexed { index, video ->
            Log.d(TAG, "  ${index + 1}. ${video.url}")
        }

        return uniqueVideos
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
        // Karena situs ini tidak memiliki multiple qualities,
        // kita bisa mengecek dari pattern path atau memberikan default
        return "HD" // Default ke HD karena kebanyakan video situs ini HD
    }
}
