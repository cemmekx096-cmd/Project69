package eu.kanade.tachiyomi.lib.lk21extractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * LK21 Video Extractor
 * - Cast/TurboVIP/Hydrax → GET via playeriframe.sbs (SSR, no JS needed)
 * - P2P → POST ke api2.php (last resort, berisiko Cloudflare)
 */
class Lk21Extractor(
    private val client: OkHttpClient,
    private val headers: Headers,
) {
    private val defaultHeaders = Headers.Builder()
        .add("Referer", "https://lk21.de/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .build()

    /**
     * Extract videos dari player URL
     * @param playerUrl URL player (sudah dalam format playeriframe.sbs/iframe/provider/slug)
     * @param serverName Label server untuk quality string
     */
    fun videosFromUrl(playerUrl: String, serverName: String = "Player"): List<Video> {
        if (playerUrl.isBlank()) return emptyList()

        val videoList = mutableListOf<Video>()

        return try {
            when {
                // P2P → POST ke api2.php (prioritas terakhir)
                playerUrl.contains("/p2p/") -> {
                    extractP2P(playerUrl, serverName, videoList)
                }
                // Cast, TurboVIP, Hydrax → GET via playeriframe.sbs
                playerUrl.contains("/cast/") ||
                playerUrl.contains("/turbovip/") ||
                playerUrl.contains("/hydrax/") -> {
                    extractViaProxy(playerUrl, serverName, videoList)
                }
                // Provider tidak dikenal → coba proxy langsung
                else -> {
                    extractViaProxy(playerUrl, serverName, videoList)
                }
            }
            videoList.distinctBy { it.url }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Extract via playeriframe.sbs proxy (Cast, TurboVIP, Hydrax)
     * Server sudah SSR → link langsung ada di HTML
     */
    private fun extractViaProxy(
        playerUrl: String,
        serverName: String,
        videoList: MutableList<Video>,
    ) {
        try {
            val response = client.newCall(GET(playerUrl, defaultHeaders)).execute()
            if (!response.isSuccessful) return

            val html = response.body.string()

            // Regex untuk .m3u8 dan .mp4
            val videoRegex = """(https?://[^\s"'<>]+(?:\.m3u8|\.mp4)[^\s"'<>]*?)["'\s]""".toRegex()
            videoRegex.findAll(html).forEach { match ->
                val videoUrl = match.groupValues[1]
                if (videoUrl.isNotBlank()) {
                    val quality = extractQuality(videoUrl, serverName)
                    videoList.add(Video(videoUrl, quality, videoUrl, defaultHeaders))
                }
            }

            // Juga cari pattern jwplayer/flowplayer
            val jwRegex = """file\s*:\s*["'](https?://[^"']+)["']""".toRegex()
            jwRegex.findAll(html).forEach { match ->
                val videoUrl = match.groupValues[1]
                if (videoUrl.isNotBlank() &&
                    (videoUrl.contains(".m3u8") || videoUrl.contains(".mp4"))
                ) {
                    val quality = extractQuality(videoUrl, serverName)
                    videoList.add(Video(videoUrl, quality, videoUrl, defaultHeaders))
                }
            }
        } catch (e: Exception) {
            // Gagal → biarkan kosong, fallback di LK21Movies.kt
        }
    }

    /**
     * Extract P2P via api2.php POST
     * P2P hanya tersedia 480p
     */
    private fun extractP2P(
        playerUrl: String,
        serverName: String,
        videoList: MutableList<Video>,
    ) {
        try {
            val slug = playerUrl.substringAfterLast("/")
            val p2pApi = "https://cloud.hownetwork.xyz/api2.php?id=$slug"

            val body = FormBody.Builder()
                .add("r", "https://lk21.de/")
                .add("d", "cloud.hownetwork.xyz")
                .build()

            val response = client.newCall(POST(p2pApi, defaultHeaders, body)).execute()
            if (!response.isSuccessful) return

            val json = JSONObject(response.body.string())
            val videoUrl = json.optString("file", "")

            if (videoUrl.isNotBlank() && videoUrl.startsWith("http")) {
                videoList.add(Video(videoUrl, "$serverName - 480p", videoUrl, defaultHeaders))
            }
        } catch (e: Exception) {
            // P2P gagal (kemungkinan Cloudflare) → biarkan kosong
        }
    }

    /**
     * Extract quality dari URL
     */
    private fun extractQuality(url: String, serverName: String): String {
        val quality = when {
            url.contains("1080", ignoreCase = true) -> "1080p"
            url.contains("720", ignoreCase = true) -> "720p"
            url.contains("480", ignoreCase = true) -> "480p"
            url.contains("360", ignoreCase = true) -> "360p"
            url.contains(".m3u8", ignoreCase = true) -> "HLS"
            url.contains(".mp4", ignoreCase = true) -> "MP4"
            else -> "Default"
        }
        return "$serverName - $quality"
    }
}
