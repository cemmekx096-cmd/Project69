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
        private const val DEFAULT_BASE_URL = "https://www.papalah.com"

        // Video CDN domains - Multiple CDNs support
        private val VIDEO_CDN_DOMAINS = listOf(
            "https://media.sslah.com",
            "https://media.aiailah.com",
            "https://media.papalah.com",
            "https://media.aalah.me",
        )
        private const val VIDEO_CDN_PRIMARY = "https://media.sslah.com"  // Default fallback
    }

    // ==================== URL Normalizer ====================

    /**
     * Normalize relative URLs to absolute URLs.
     *
     * Examples:
     * - "videos/18/xxx.mp4" → "https://media.sslah.com/videos/18/xxx.mp4"
     * - "/videos/18/xxx.mp4" → "https://media.sslah.com/videos/18/xxx.mp4"
     * - "https://media.sslah.com/..." → unchanged
     */
    private fun normalizeUrl(url: String, baseUrl: String = DEFAULT_BASE_URL): String {
        return when {
            // Already absolute URL with protocol (http:// or https://)
            url.startsWith("http://") || url.startsWith("https://") -> {
                Log.d(TAG, "✅ Absolute URL: $url")
                url
            }

            // Protocol-relative URL (//example.com/...)
            url.startsWith("//") -> {
                val normalized = "https:$url"
                Log.d(TAG, "✅ Protocol-relative: $url -> $normalized")
                normalized
            }

            // VIDEO CDN PATH - CRITICAL: Must use media CDN, NOT www!
            // Patterns: "videos/18/xxx.mp4" or "/videos/18/xxx.mp4"
            url.startsWith("videos/") || url.startsWith("/videos/") -> {
                val cleanPath = url.removePrefix("/")
                val normalized = "$VIDEO_CDN_PRIMARY/$cleanPath"
                Log.d(TAG, "🎥 Video CDN path detected: $url -> $normalized")
                normalized
            }

            // Relative URL with leading slash (/tag/something, /v/12345)
            url.startsWith("/") -> {
                val normalized = "$baseUrl$url"
                Log.d(TAG, "✅ Relative with slash: $url -> $normalized")
                normalized
            }

            // Relative URL without leading slash (v/12345, tag/busty)
            else -> {
                val normalized = "$baseUrl/$url"
                Log.d(TAG, "⚠️ Fixing relative URL without slash: $url -> $normalized")
                normalized
            }
        }
    }

    // ==================== Extract from HTML (SIMPLIFIED) ====================

    fun extractFromHtml(html: String, referer: String = ""): List<Video> {
        val videos = mutableListOf<Video>()

        Log.d(TAG, "=== START EXTRACTION ===")
        Log.d(TAG, "Referer: $referer")

        // METHOD 1: Extract dari <video> tag dengan src attribute
        // Pattern: <video id="my-video_html5_api" ... src="https://media.sslah.com/videos/99/xxx.mp4">
        Regex("""<video[^>]*\s+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                val videoUrl = normalizeUrl(match.groupValues[1])
                Log.d(TAG, "  ✅ Found <video src>: $videoUrl")
                videos.add(Video(videoUrl, "Papalah - Direct", videoUrl, headers))
            }

        // METHOD 2: Extract dari <source> tag
        // Pattern: <source src="https://media.sslah.com/videos/99/xxx.mp4" type="video/mp4">
        Regex("""<source[^>]*\s+src\s*=\s*["']([^"']+)["'][^>]*type\s*=\s*["']video/mp4["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                val videoUrl = normalizeUrl(match.groupValues[1])
                Log.d(TAG, "  ✅ Found <source src>: $videoUrl")
                videos.add(Video(videoUrl, "Papalah - Source", videoUrl, headers))
            }
  
        // METHOD 3: Reverse pattern - type first, src second
        Regex("""<source[^>]*\s+type\s*=\s*["']video/mp4["'][^>]*\s+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                val videoUrl = normalizeUrl(match.groupValues[1])
                Log.d(TAG, "  ✅ Found <source type+src>: $videoUrl")
                videos.add(Video(videoUrl, "Papalah - Source Alt", videoUrl, headers))
            }

        // METHOD 4: Fallback - cari semua .mp4 URLs di HTML
        if (videos.isEmpty()) {
            Log.d(TAG, "  ⚠️ No videos found via regex, trying fallback...")

            // Pattern: Cari semua URL yang berakhiran .mp4
            Regex("""(https?://[^\s"'<>]+\.mp4[^\s"'<>]*)""")
                .findAll(html)
                .forEach { match ->
                    val videoUrl = match.groupValues[1]
                    Log.d(TAG, "  ✅ Found mp4 URL (fallback): $videoUrl")
                    videos.add(Video(videoUrl, "Papalah - Fallback", videoUrl, headers))
                }
        }

        val uniqueVideos = videos.distinctBy { it.url }
        Log.d(TAG, "=== EXTRACTION COMPLETE ===")
        Log.d(TAG, "Total videos found: ${uniqueVideos.size}")

        if (uniqueVideos.isEmpty()) {
            Log.e(TAG, "❌ NO VIDEOS FOUND! Check HTML structure.")
        }

        return uniqueVideos
    }
}
