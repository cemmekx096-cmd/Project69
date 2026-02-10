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
            "media.aalah.me",
        )
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
                videos.add(createVideo(url, "Direct Video"))
            }

        // METHOD 2: Source tags
        Log.d(TAG, "🔍 Searching for <source> tags...")
        val sourcePattern = """<source[^>]+src\s*=\s*["']([^"']+\.mp4)["'][^>]+type\s*=\s*["']video/mp4["']"""
        Regex(sourcePattern, RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEachIndexed { index, match ->
                val url = match.groupValues[1].trim()
                Log.d(TAG, "  ✅ Found source #${index + 1}: $url")
                videos.add(createVideo(url, "Source Tag"))
            }

        // METHOD 3: Debug - simpan snippet HTML untuk analisis
        if (videos.isEmpty()) {
            Log.e(TAG, "❌ NO VIDEOS FOUND!")

            // Simpan HTML snippet untuk debugging
            val videoSection = extractVideoSection(html)
            Log.d(TAG, "📄 HTML Video Section (500 chars):")
            Log.d(TAG, videoSection)

            // Coba cari semua .mp4 di HTML
            Log.d(TAG, "🔍 Trying fallback - all .mp4 URLs...")
            val allMp4Pattern = """https?://[^\s"'<>]+\.mp4"""
            Regex(allMp4Pattern, RegexOption.IGNORE_CASE)
                .findAll(html)
                .forEachIndexed { index, match ->
                    val url = match.value.trim()
                    Log.d(TAG, "  🟡 Found mp4 #${index + 1}: $url")
                    if (isValidVideoUrl(url)) {
                        Log.d(TAG, "  ✅ Valid video: $url")
                        videos.add(createVideo(url, "Fallback"))
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

    private fun detectQualityFromUrl(url: String): String {
        // Karena situs ini tidak memiliki multiple qualities,
        // kita bisa mengecek dari pattern path atau memberikan default
        return "HD" // Default ke HD karena kebanyakan video situs ini HD
    }
}
