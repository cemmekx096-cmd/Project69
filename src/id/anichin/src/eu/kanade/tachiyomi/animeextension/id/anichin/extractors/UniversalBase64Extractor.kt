package eu.kanade.tachiyomi.animeextension.id.animesite.extractors

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.anichinextractor.AnichinExtractor
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.rubyvidhubextractor.RubyVidHubExtractor
import eu.kanade.tachiyomi.lib.rumbleextractor.RumbleExtractor
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

/**
 * Universal extractor untuk handle base64 encoded iframes
 * Auto-detect video source dan route ke extractor yang sesuai
 *
 * Supports:
 * - Anichin/VIP
 * - RubyVidHub
 * - Dailymotion
 * - Rumble
 * - URL shorteners (short.icu, dll)
 *
 * @param client OkHttpClient instance untuk network requests
 */
class UniversalBase64Extractor(private val client: OkHttpClient) {

    /**
     * Extract videos dari base64 encoded string
     *
     * @param base64 Base64 encoded iframe HTML
     * @param label Label dari option tag (akan digunakan sebagai prefix)
     * @return List of Video dari semua sources yang terdeteksi
     */
    fun extractFromBase64(base64: String, label: String): List<Video> {
        return try {
            // Skip jika ada marker [ADS]
            if (label.contains("ADS", ignoreCase = true)) {
                return emptyList()
            }

            // Decode base64
            val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
            val decodedHtml = String(decodedBytes, Charsets.UTF_8)

            // Parse iframe src
            val doc = Jsoup.parse(decodedHtml)
            val iframeSrc = doc.select("iframe").attr("src")

            if (iframeSrc.isEmpty()) return emptyList()

            // Route berdasarkan domain/pattern
            routeToExtractor(iframeSrc, label)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Route URL ke extractor yang sesuai
     */
    private fun routeToExtractor(url: String, label: String): List<Video> {
        return when {
            // Anichin
            url.contains("anichin", ignoreCase = true) -> {
                AnichinExtractor(client).videosFromUrl(url, label)
            }

            // RubyVidHub
            url.contains("rubyvidhub", ignoreCase = true) -> {
                RubyVidHubExtractor(client).videosFromUrl(url, label)
            }

            // Dailymotion
            url.contains("dailymotion", ignoreCase = true) -> {
                DailymotionExtractor(client).videosFromUrl(url, label)
            }

            // Rumble
            url.contains("rumble", ignoreCase = true) -> {
                RumbleExtractor(client).videosFromUrl(url, label)
            }

            // URL Shortener - follow redirect dulu
            url.contains("short.", ignoreCase = true) -> {
                handleShortUrl(url, label)
            }

            // Unknown source - try generic extraction
            else -> {
                tryGenericExtraction(url, label)
            }
        }
    }

    /**
     * Handle URL shorteners (short.icu, dll)
     * Follow redirect kemudian route ulang
     */
    private fun handleShortUrl(shortUrl: String, label: String): List<Video> {
        return try {
            val response = client.newCall(
                okhttp3.Request.Builder()
                    .url(shortUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build(),
            ).execute()

            val finalUrl = response.request.url.toString()

            // Route final URL ke extractor yang sesuai
            routeToExtractor(finalUrl, label)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Try generic extraction untuk unknown sources
     * Mencari pattern umum video URLs
     */
    private fun tryGenericExtraction(url: String, label: String): List<Video> {
        return try {
            val response = client.newCall(
                okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", url)
                    .build(),
            ).execute()

            val html = response.body.string()
            val videoList = mutableListOf<Video>()

            // Pattern umum untuk video URLs
            val patterns = listOf(
                // M3U8 URLs
                """"(https://[^"]*\.m3u8[^"]*)"""".toRegex(),
                """file:\s*["']([^"']+\.m3u8[^"']*)["']""".toRegex(),

                // MP4 URLs
                """"(https://[^"]*\.mp4[^"]*)"""".toRegex(),
                """file:\s*["']([^"']+\.mp4[^"']*)["']""".toRegex(),

                // src attributes
                """src=["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""".toRegex(),
            )

            for (pattern in patterns) {
                pattern.find(html)?.groupValues?.get(1)?.let { videoUrl ->
                    videoList.add(
                        Video(
                            url = videoUrl,
                            quality = label,
                            videoUrl = videoUrl,
                            headers = okhttp3.Headers.headersOf(
                                "Referer", url,
                                "User-Agent", "Mozilla/5.0",
                            ),
                        ),
                    )
                    return videoList // Return setelah first match
                }
            }

            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Extract semua videos dari document yang mengandung multiple <option> tags
     *
     * @param document Jsoup Document dari halaman anime
     * @return List of Video dari semua sources
     */
    fun extractAllFromDocument(document: org.jsoup.nodes.Document): List<Video> {
        val videoList = mutableListOf<Video>()

        // Find all <option> tags dengan value (base64) dan data-index
        document.select("option[value][data-index]").forEach { option ->
            val base64 = option.attr("value")
            val label = option.text().trim()

            if (base64.isNotEmpty()) {
                videoList.addAll(extractFromBase64(base64, label))
            }
        }

        return videoList
    }
}
