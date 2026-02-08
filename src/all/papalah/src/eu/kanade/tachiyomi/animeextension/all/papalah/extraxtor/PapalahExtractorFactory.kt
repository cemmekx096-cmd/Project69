package eu.kanade.tachiyomi.animeextension.all.papalah.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import okhttp3.OkHttpClient

class PapalahExtractorFactory(
    private val client: OkHttpClient,
    private val headers: Headers
) {

    private val extractor = PapalahExtractor(client, headers)

    // ===================== Extract from URL ==============================

    fun extractVideos(url: String, prefix: String = ""): List<Video> {
        return when {
            // Direct video formats
            url.contains(".m3u8") -> extractor.m3u8Extractor(url, prefix)
            url.contains(".mp4") -> listOf(Video(url, "${prefix}MP4", url, headers))

            // Embed platforms
            url.contains("streamtape") -> extractor.streamtapeExtractor(url, prefix)
            url.contains("doodstream") || url.contains("dood") -> extractor.doodExtractor(url, prefix)
            url.contains("mixdrop") -> extractor.mixdropExtractor(url, prefix)
            url.contains("fembed") || url.contains("feurl") -> extractor.fembedExtractor(url, prefix)
            url.contains("upstream") -> extractor.upstreamExtractor(url, prefix)
            url.contains("streamwish") || url.contains("strwish") -> extractor.streamwishExtractor(url, prefix)
            url.contains("filemoon") -> extractor.filemoonExtractor(url, prefix)
            url.contains("vidguard") -> extractor.vidguardExtractor(url, prefix)

            else -> emptyList()
        }
    }

    // ==================== Extract from HTML ==============================

    fun extractFromHtml(html: String, referer: String = ""): List<Video> {
        val videos = mutableListOf<Video>()

        // 1. Extract from iframe sources
        extractIframesFromHtml(html).forEach { iframeUrl ->
            videos.addAll(extractVideos(iframeUrl))
        }

        // 2. Extract from video tags
        extractVideoTagsFromHtml(html).forEach { videoUrl ->
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

        return videos.distinctBy { it.url }
    }

    // ===================== Helper Functions ==============================

    private fun extractIframesFromHtml(html: String): List<String> {
        val iframes = mutableListOf<String>()

        // Pattern 1: <iframe src="...">
        Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                iframes.add(match.groupValues[1])
            }

        // Pattern 2: data-src for lazy loading
        Regex("""<iframe[^>]+data-src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                iframes.add(match.groupValues[1])
            }

        return iframes.filter { it.isNotBlank() }
    }

    private fun extractVideoTagsFromHtml(html: String): List<String> {
        val videos = mutableListOf<String>()

        // Pattern 1: <video src="...">
        Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                videos.add(match.groupValues[1])
            }

        // Pattern 2: <source src="...">
        Regex("""<source[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                videos.add(match.groupValues[1])
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
