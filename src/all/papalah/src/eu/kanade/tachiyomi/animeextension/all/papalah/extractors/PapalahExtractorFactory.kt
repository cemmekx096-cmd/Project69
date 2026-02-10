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
        // Semua CDN domains yang digunakan - URUTAN PRIORITAS
        private val VIDEO_CDN_DOMAINS = listOf(
            "media.sslah.com",      // Primary (paling aktif)
            "media.papalah.com",    // Secondary
            "media.aiailah.com",    // Original dari JS
            "media.aalah.me:8443",  // Port specific
            "media.aalah.me",       // Fallback
        )

        // Video file extensions yang didukung
        private val VIDEO_EXTENSIONS = listOf(".mp4", ".m3u8")
    }

    fun extractFromHtml(html: String, referer: String = ""): List<Video> {
        val videos = mutableListOf<Video>()

        Log.d(TAG, "=== START EXTRACTION ===")
        Log.d(TAG, "HTML length: ${html.length} chars")
        Log.d(TAG, "Referer: $referer")

        // METHOD 0: Decode obfuscated JavaScript (PRIORITY 1)
        Log.d(TAG, "🔐 Searching for obfuscated JavaScript...")
        val jsDecodedUrl = extractFromObfuscatedJs(html)

        if (jsDecodedUrl != null) {
            Log.d(TAG, "🎯 Decoded from JS: $jsDecodedUrl")

            // Generate URLs for all domains
            VIDEO_CDN_DOMAINS.forEach { targetDomain ->
                val transformedUrl = transformDomain(jsDecodedUrl, targetDomain)
                val qualityLabel = getDomainLabel(targetDomain)
                
                Log.d(TAG, "  🔄 $targetDomain: $transformedUrl")
                videos.add(createVideo(transformedUrl, qualityLabel))
            }

            Log.d(TAG, "✅ Generated ${videos.size} URLs from JS decoding")
        }

        // METHOD 1: Video tag langsung (PRIORITY 2)
        if (videos.isEmpty()) {
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
        }

        // METHOD 2: Source tags (PRIORITY 3)
        if (videos.isEmpty()) {
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
        }

        // METHOD 3: Fallback - cari semua URL mp4 (PRIORITY 4)
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

        // METHOD 4: Debug - cari semua .mp4 jika masih kosong (PRIORITY 5)
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

    /**
     * EXTRACT FROM OBFUSCATED JAVASCRIPT
     * Pattern yang ditemukan:
     * const _array = ["001","https%3A","dia.aiai","%2F%2Fme","%2F64%2F",".mp4_626","877ec6c8","870db6a8","6058f984","lah.com%","64c7951b","2Fvideos"];
     * const _index = [11,0,2,1,5,10,7,9,8,3,6,4];
     */
    private fun extractFromObfuscatedJs(html: String): String? {
        try {
            // Pattern untuk mendeteksi kode obfuscated
            val jsPattern = """<script[^>]*>.*?const\s+_\w+\s*=\s*\[(.*?)\].*?const\s+_\w+\s*=\s*\[(.*?)\].*?</script>"""
                .toRegex(RegexOption.DOT_MATCHES_ALL)

            val match = jsPattern.find(html)
            if (match == null) {
                Log.d(TAG, "❌ No obfuscated JS pattern found")
                return null
            }

            val arrayStr = match.groupValues[1]
            val indexStr = match.groupValues[2]

            Log.d(TAG, "🎯 Found obfuscated JS pattern")
            Log.d(TAG, "   Array: $arrayStr")
            Log.d(TAG, "   Index: $indexStr")

            // Parse array parts
            val parts = parseStringArray(arrayStr)
            val indices = parseIntArray(indexStr)

            if (parts.isEmpty() || indices.isEmpty() || parts.size != indices.size) {
                Log.d(TAG, "❌ Invalid arrays size")
                return null
            }

            // Reconstruct encoded URL
            val encoded = StringBuilder()
            for (i in indices.indices) {
                val pos = indices.indexOf(i)
                if (pos != -1 && pos < parts.size) {
                    encoded.append(parts[pos])
                }
            }

            val encodedUrl = encoded.toString()
            if (encodedUrl.isEmpty()) {
                Log.d(TAG, "❌ Failed to reconstruct URL")
                return null
            }

            Log.d(TAG, "🔗 Encoded URL: $encodedUrl")

            // URL decode
            val decodedUrl = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
            Log.d(TAG, "🔓 Decoded URL: $decodedUrl")

            // Remove suffix (_626001, _123456, etc)
            val cleanUrl = decodedUrl.replace(Regex("""_\d+$"""), "")
            Log.d(TAG, "🧹 Clean URL: $cleanUrl")
            
            // Validate URL format
            return if (cleanUrl.matches(Regex("""https?://[^/]+/videos/[^/]+/.+\.mp4"""))) {
                cleanUrl
            } else {
                Log.d(TAG, "❌ Invalid URL format: $cleanUrl")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error decoding JS: ${e.message}")
            return null
        }
    }

    /**
     * TRANSFORM DOMAIN
     * Contoh: https://media.aiailah.com/videos/xx/xxxxxxxxxxxx.mp4
     *   → https://media.sslah.com/videos/xx/xxxxxxxxxxxx.mp4
     */
    private fun transformDomain(originalUrl: String, targetDomain: String): String {
        return try {
            val urlRegex = Regex("""https?://([^/]+)(/.*)""")
            val match = urlRegex.find(originalUrl)
            
            if (match != null) {
                val path = match.groupValues[2]
                "https://$targetDomain$path"
            } else {
                originalUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error transforming domain: ${e.message}")
            originalUrl
        }
    }

    /**
     * GET DOMAIN LABEL for video quality
     */
    private fun getDomainLabel(domain: String): String {
        return when {
            domain.contains("sslah.com") -> "HD (SSLAH)"
            domain.contains("papalah.com") -> "SD (Papalah)"
            domain.contains("aiailah.com") -> "Original (AIAILAH)"
            domain.contains("aalah.me:8443") -> "Port 8443"
            domain.contains("aalah.me") -> "Fallback (AALAH)"
            else -> "Unknown"
        }
    }

    /**
     * PARSE STRING ARRAY dari JavaScript
     */
    private fun parseStringArray(str: String): List<String> {
        return try {
            // Clean and split
            str.split(",")
                .map { it.trim().removeSurrounding("\"", "\"").removeSurrounding("'", "'") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * PARSE INTEGER ARRAY dari JavaScript
     */
    private fun parseIntArray(str: String): List<Int> {
        return try {
            str.split(",")
                .map { it.trim().toIntOrNull() ?: -1 }
                .filter { it >= 0 }
        } catch (e: Exception) {
            emptyList()
        }
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
            url.contains(domain.replace(":8443", "")) // Remove port untuk matching
        }

        if (!isValidDomain) {
            Log.d(TAG, "❌ Invalid domain for URL: $url")
        }

        return isValidDomain
    }

    private fun createVideo(url: String, source: String): Video {
        // Coba detect quality dari URL (jika ada pattern)
        val quality = detectQualityFromUrl(url)
        val label = "Papalah ($source)" + if (quality.isNotEmpty()) " - $quality" else ""

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