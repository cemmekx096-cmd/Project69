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

    // ==================== URL Normalizer ================================

    private fun normalizeUrl(url: String, baseUrl: String = "https://www.papalah.com"): String {
        return when {
            // Already absolute URL
            url.startsWith("http://") || url.startsWith("https://") -> url

            // Relative URL with leading slash
            url.startsWith("/") -> "$baseUrl$url"

            // Relative URL without leading slash (THIS IS THE BUG!)
            else -> {
                Log.w(TAG, "Fixing relative URL without slash: $url")
                "$baseUrl/$url"
            }
        }
    }

    // ===================== Extract from URL ==============================

    fun extractVideos(url: String, prefix: String = ""): List<Video> {
        val normalizedUrl = normalizeUrl(url) // 👈 NORMALIZE DULU

        return when {
            // Direct video formats
            normalizedUrl.contains(".m3u8") -> extractor.m3u8Extractor(normalizedUrl, prefix)
            normalizedUrl.contains(".mp4") -> listOf(Video(normalizedUrl, "${prefix}MP4", normalizedUrl, headers))

            // Embed platforms
            normalizedUrl.contains("streamtape") -> extractor.streamtapeExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("doodstream") || normalizedUrl.contains("dood") -> extractor.doodExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("mixdrop") -> extractor.mixdropExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("fembed") || normalizedUrl.contains("feurl") -> extractor.fembedExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("upstream") -> extractor.upstreamExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("streamwish") || normalizedUrl.contains("strwish") -> extractor.streamwishExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("filemoon") -> extractor.filemoonExtractor(normalizedUrl, prefix)
            normalizedUrl.contains("vidguard") -> extractor.vidguardExtractor(normalizedUrl, prefix)

            else -> emptyList()
        }
    }

    // ==================== Extract from HTML ==============================

    fun extractFromHtml(html: String, referer: String = ""): List<Video> {
        val videos = mutableListOf<Video>()

        Log.d(TAG, "=== START EXTRACTION ===")
        Log.d(TAG, "Referer: $referer")

        // 1. Extract from iframe sources
        extractIframesFromHtml(html).forEach { iframeUrl ->
            Log.d(TAG, "Found iframe: $iframeUrl")
            videos.addAll(extractVideos(iframeUrl))
        }

        // 2. Extract from video tags
        extractVideoTagsFromHtml(html).forEach { videoUrl ->
            Log.d(TAG, "Found video tag: $videoUrl")
            videos.add(Video(videoUrl, "Direct Video", videoUrl, headers))
        }

        // 3. Extract from packed JS
        extractPackedJsFromHtml(html).forEach { packedJs ->
            extractor.unpackJs(packedJs)?.let { unpacked ->
                extractUrlsFromText(unpacked).forEach { url ->
                    videos.addAll(extractVideos(url, "Unpacked - "))
                }
            }
        }

        // 4. Extract direct URLs from text
        extractUrlsFromText(html).forEach { url ->
            if (url.contains(".m3u8") || url.contains(".mp4")) {
                videos.addAll(extractVideos(url))
            }
        }

        Log.d(TAG, "Total videos found: ${videos.size}")
        return videos.distinctBy { it.url }
    }

    // ===================== Helper Functions ==============================

    private fun extractIframesFromHtml(html: String): List<String> {
        val iframes = mutableListOf<String>()

        // Pattern 1: <iframe src="...">
        Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                iframes.add(normalizeUrl(match.groupValues[1])) // 👈 FIX
            }

        // Pattern 2: data-src for lazy loading
        Regex("""<iframe[^>]+data-src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                iframes.add(normalizeUrl(match.groupValues[1])) // 👈 FIX
            }

        return iframes.filter { it.isNotBlank() }
    }

    private fun extractVideoTagsFromHtml(html: String): List<String> {
        val videos = mutableListOf<String>()

        // Pattern 1: <video src="...">
        Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                videos.add(normalizeUrl(match.groupValues[1])) // 👈 FIX
            }

        // Pattern 2: <source src="...">
        Regex("""<source[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                videos.add(normalizeUrl(match.groupValues[1])) // 👈 FIX
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
