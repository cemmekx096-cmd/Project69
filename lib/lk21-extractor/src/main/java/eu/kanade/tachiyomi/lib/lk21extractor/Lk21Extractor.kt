package eu.kanade.tachiyomi.lib.lk21extractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class Lk21Extractor(
    private val client: OkHttpClient,
    private val headers: Headers
) {

    /**
     * Extract video URLs dari iframe URL
     * @param iframeUrl URL iframe dari player (misal: https://playeriframe.sbs/iframe/p2p/xxxxx)
     * @param serverName Nama server untuk label (P2P, TurboVIP, Cast, Hydrax)
     * @return List of Video objects
     */
    fun videosFromUrl(iframeUrl: String, serverName: String = "Unknown"): List<Video> {
        return try {
            when {
                iframeUrl.contains("playeriframe.sbs") -> extractFromPlayerIframe(iframeUrl, serverName)
                else -> extractGeneric(iframeUrl, serverName)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Extract dari playeriframe.sbs
     */
    private fun extractFromPlayerIframe(iframeUrl: String, serverName: String): List<Video> {
        val videoList = mutableListOf<Video>()

        try {
            val response = client.newCall(GET(iframeUrl, headers)).execute()
            val document = response.asJsoup()

            // Method 1: Cari direct video URLs di sources
            document.select("video source[src], video[src]").forEach { element ->
                val videoUrl = element.attr("src").takeIf { it.isNotBlank() }
                    ?: element.attr("data-src")

                if (videoUrl.isNotBlank() && videoUrl.startsWith("http")) {
                    val quality = extractQuality(videoUrl) ?: "Default"
                    videoList.add(
                        Video(
                            url = videoUrl,
                            quality = "$serverName - $quality",
                            videoUrl = videoUrl,
                            headers = headers
                        )
                    )
                }
            }

            // Method 2: Cari di script tags untuk file: atau sources:
            document.select("script").forEach { script ->
                val scriptText = script.html()
                
                // Pattern: file: "https://..."
                val filePattern = """file:\s*["']([^"']+)["']""".toRegex()
                filePattern.findAll(scriptText).forEach { match ->
                    val videoUrl = match.groupValues[1]
                    if (videoUrl.startsWith("http")) {
                        val quality = extractQuality(videoUrl) ?: "Default"
                        videoList.add(
                            Video(
                                url = videoUrl,
                                quality = "$serverName - $quality",
                                videoUrl = videoUrl,
                                headers = headers
                            )
                        )
                    }
                }

                // Pattern: sources: [{file: "https://..."}]
                val sourcesPattern = """sources:\s*\[?\{[^}]*file:\s*["']([^"']+)["']""".toRegex()
                sourcesPattern.findAll(scriptText).forEach { match ->
                    val videoUrl = match.groupValues[1]
                    if (videoUrl.startsWith("http")) {
                        val quality = extractQuality(videoUrl) ?: "Default"
                        videoList.add(
                            Video(
                                url = videoUrl,
                                quality = "$serverName - $quality",
                                videoUrl = videoUrl,
                                headers = headers
                            )
                        )
                    }
                }
            }

            // Method 3: Cari iframe nested
            document.select("iframe[src]").forEach { iframe ->
                val nestedUrl = iframe.attr("src")
                if (nestedUrl.startsWith("http") && nestedUrl != iframeUrl) {
                    videoList.addAll(extractGeneric(nestedUrl, "$serverName (Nested)"))
                }
            }

        } catch (e: Exception) {
            // Return empty jika gagal
        }

        return videoList.distinctBy { it.url }
    }

    /**
     * Generic extractor untuk iframe lain
     */
    private fun extractGeneric(iframeUrl: String, serverName: String): List<Video> {
        val videoList = mutableListOf<Video>()

        try {
            val response = client.newCall(GET(iframeUrl, headers)).execute()
            val document = response.asJsoup()

            // Cari video tags
            document.select("video source[src], video[src]").forEach { element ->
                val videoUrl = element.attr("src").takeIf { it.isNotBlank() }
                    ?: element.attr("data-src")

                if (videoUrl.isNotBlank() && videoUrl.startsWith("http")) {
                    val quality = extractQuality(videoUrl) ?: "Default"
                    videoList.add(
                        Video(
                            url = videoUrl,
                            quality = "$serverName - $quality",
                            videoUrl = videoUrl,
                            headers = headers
                        )
                    )
                }
            }

            // Cari di script untuk .m3u8 atau .mp4
            document.select("script").forEach { script ->
                val scriptText = script.html()
                val urlPattern = """https?://[^\s"'<>]+\.(?:m3u8|mp4)""".toRegex()

                urlPattern.findAll(scriptText).forEach { match ->
                    val videoUrl = match.value
                    val quality = extractQuality(videoUrl) ?: "Default"
                    videoList.add(
                        Video(
                            url = videoUrl,
                            quality = "$serverName - $quality",
                            videoUrl = videoUrl,
                            headers = headers
                        )
                    )
                }
            }

        } catch (e: Exception) {
            // Return empty jika gagal
        }

        return videoList.distinctBy { it.url }
    }

    /**
     * Extract quality dari URL atau filename
     */
    private fun extractQuality(url: String): String? {
        return when {
            url.contains("1080") || url.contains("1080p") -> "1080p"
            url.contains("720") || url.contains("720p") -> "720p"
            url.contains("480") || url.contains("480p") -> "480p"
            url.contains("360") || url.contains("360p") -> "360p"
            url.contains(".m3u8") -> "HLS"
            url.contains(".mp4") -> "MP4"
            else -> null
        }
    }
}
