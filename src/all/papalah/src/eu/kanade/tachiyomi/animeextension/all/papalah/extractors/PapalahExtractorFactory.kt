package eu.kanade.tachiyomi.animeextension.all.papalah.extractors

import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class PapalahExtractorFactory(
    private val client: OkHttpClient,
    private val headers: Headers,
) {

    private val extractor = PapalahExtractor(client, headers)
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
        private const val VIDEO_CDN_PRIMARY = "https://media.sslah.com" // Default fallback
    }

    // ==================== URL Normalizer (FIX untuk .comv/ bug) ====================

    /**
     * Normalize relative URLs to absolute URLs.
     *
     * CRITICAL RULES:
     * 1. Video paths (videos/...) MUST use media.papalah.com (streaming CDN)
     * 2. Using www.papalah.com for videos will cause DOWNLOAD instead of streaming!
     * 3. Fixes bug where "v/12345" becomes "papalah.comv/12345"
     *
     * Examples:
     * - "videos/18/xxx.mp4" → "https://media.papalah.com/videos/18/xxx.mp4" ✅
     * - "/videos/18/xxx.mp4" → "https://media.papalah.com/videos/18/xxx.mp4" ✅
     * - "https://media.sslah.com/..." → unchanged ✅
     * - "v/12345" → "https://www.papalah.com/v/12345" ✅
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

            // VIDEO CDN PATH - CRITICAL: Must use media.papalah.com, NOT www!
            // Patterns: "videos/18/xxx.mp4" or "/videos/18/xxx.mp4"
            // Why: www.papalah.com/videos/... triggers DOWNLOAD instead of streaming
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

            // Relative URL without leading slash (v/12345, tag/busty) - THIS WAS THE BUG!
            // Bug example: "v/12345" without proper handling becomes "papalah.comv/12345"
            else -> {
                val normalized = "$baseUrl/$url"
                Log.w(TAG, "⚠️ Fixing relative URL without slash: $url -> $normalized")
                normalized
            }
        }
    }

    // ===================== Extract from URL ==============================

    fun extractVideos(url: String, prefix: String = ""): List<Video> {
        val normalizedUrl = normalizeUrl(url)

        Log.d(TAG, "Extracting from: $normalizedUrl")

        return when {
            // Direct video formats
            normalizedUrl.contains(".m3u8") -> extractor.m3u8Extractor(normalizedUrl, prefix)
            normalizedUrl.contains(".mp4") || normalizedUrl.contains(".webm") -> {
                listOf(Video(normalizedUrl, "${prefix}MP4", normalizedUrl, headers))
            }

            // Embed platforms
            normalizedUrl.contains("streamtape") -> extractor.streamtapeExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("doodstream") || normalizedUrl.contains("dood") -> extractor.doodExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("mixdrop") -> extractor.mixdropExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("fembed") || normalizedUrl.contains("feurl") -> extractor.fembedExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("upstream") -> extractor.upstreamExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("streamwish") || normalizedUrl.contains("strwish") -> extractor.streamwishExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("filemoon") -> extractor.filemoonExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("vidguard") -> extractor.vidguardExtractor(normalizedUrl, prefix)

            else -> {
                Log.w(TAG, "⚠️ No extractor found for: $normalizedUrl")
                emptyList()
            }
        }
    }

    // ==================== Extract from HTML ==============================

    fun extractFromHtml(html: String, referer: String = ""): List<Video> {
        val videos = mutableListOf<Video>()

        Log.d(TAG, "=== START EXTRACTION ===")
        Log.d(TAG, "Referer: $referer")

        // 1. Extract from iframe sources
        val iframes = extractIframesFromHtml(html)
        Log.d(TAG, "Found ${iframes.size} iframe(s)")
        iframes.forEach { iframeUrl ->
            Log.d(TAG, "  └─ iframe: $iframeUrl")
            videos.addAll(extractVideos(iframeUrl))
        }

        // 2. Extract from video tags
        val videoTags = extractVideoTagsFromHtml(html)
        Log.d(TAG, "Found ${videoTags.size} video tag(s)")
        videoTags.forEach { videoUrl ->
            Log.d(TAG, "  └─ video: $videoUrl")
            videos.add(Video(videoUrl, "Direct Video", videoUrl, headers))
        }

        // 3. Extract from packed JS
        val packedJs = extractPackedJsFromHtml(html)
        Log.d(TAG, "Found ${packedJs.size} packed JS block(s)")
        packedJs.forEach { packed ->
            extractor.unpackJs(packed)?.let { unpacked ->
                extractUrlsFromText(unpacked).forEach { url ->
                    if (url.contains(".m3u8") || url.contains(".mp4")) {
                        Log.d(TAG, "  └─ unpacked: $url")
                        videos.addAll(extractVideos(url, "Unpacked - "))
                    }
                }
            }
        }

        // 4. Extract direct URLs from text
        val directUrls = extractUrlsFromText(html).filter {
            it.contains(".m3u8") || it.contains(".mp4") || it.contains(".webm")
        }
        Log.d(TAG, "Found ${directUrls.size} direct URL(s)")
        directUrls.forEach { url ->
            Log.d(TAG, "  └─ direct: $url")
            videos.addAll(extractVideos(url))
        }

        val uniqueVideos = videos.distinctBy { it.url }
        Log.d(TAG, "=== EXTRACTION COMPLETE ===")
        Log.d(TAG, "Total videos found: ${uniqueVideos.size}")

        return uniqueVideos
    }

    // ===================== Helper Functions ==============================

    private fun extractIframesFromHtml(html: String): List<String> {
        val iframes = mutableListOf<String>()

        // Pattern 1: <iframe src="...">
        Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                iframes.add(normalizeUrl(match.groupValues[1]))
            }

        // Pattern 2: data-src for lazy loading
        Regex("""<iframe[^>]+data-src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                iframes.add(normalizeUrl(match.groupValues[1]))
            }

        return iframes.filter { it.isNotBlank() }
    }

    private fun extractVideoTagsFromHtml(html: String): List<String> {
        val videos = mutableListOf<String>()

        // Pattern 1: <video src="...">
        Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                videos.add(normalizeUrl(match.groupValues[1]))
            }

        // Pattern 2: <source src="..."> inside <video> tag
        Regex("""<source[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                videos.add(normalizeUrl(match.groupValues[1]))
            }

        return videos.filter { it.isNotBlank() }
    }

    private fun extractPackedJsFromHtml(html: String): List<String> {
        val packed = mutableListOf<String>()

        Regex("""eval\(function\(p,a,c,k,e,d\).*?\}\(.*?\)\)""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html)
            .forEach { match ->
                packed.add(match.value)
            }

        return packed
    }

    private fun extractUrlsFromText(text: String): List<String> {
        val urls = mutableListOf<String>()

        // Pattern: https://... atau http://...
        Regex("""https?://[^\s"'<>]+""")
            .findAll(text)
            .forEach { match ->
                urls.add(match.value)
            }

        return urls.filter { it.isNotBlank() }
    }
}
