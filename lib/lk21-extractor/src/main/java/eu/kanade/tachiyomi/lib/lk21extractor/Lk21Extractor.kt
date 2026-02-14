package eu.kanade.tachiyomi.lib.lk21extractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.json.JSONObject

/**
 * LK21 Video Extractor
 * - P2P → GET api2.php (JSON response)
 * - TurboVIP → GET turbovidhls.com → data-hash attribute
 * - Cast → GET f16px.com → regex master.m3u8
 * - Hydrax → iframe fallback (implementasi nanti via abyss-extractor)
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

    fun videosFromUrl(playerUrl: String, serverName: String = "Player"): List<Video> {
        if (playerUrl.isBlank()) return emptyList()

        return try {
            // Extract provider dan slug dari URL
            // Format bisa: "playeriframe.sbs/iframe/turbovip/slug" atau "turbovip/slug"
            val provider = when {
                playerUrl.contains("playeriframe.sbs") ->
                    playerUrl.substringAfter("iframe/").substringBefore("/")
                else ->
                    playerUrl.substringBefore("/")
            }
            val slug = playerUrl.substringAfterLast("/")

            when (provider) {
                "cast" -> extractCast(slug, serverName)
                "turbovip" -> extractTurboVip(slug, serverName)
                "p2p" -> extractP2P(slug, serverName)
                "hydrax" -> emptyList() // fallback ke iframe
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * P2P → GET api2.php?id={slug} → JSON {file, type}
     */
    private fun extractP2P(slug: String, serverName: String): List<Video> {
        return try {
            val apiUrl = "https://cloud.hownetwork.xyz/api2.php?id=$slug"

            val response = client.newCall(GET(apiUrl, defaultHeaders)).execute()
            if (!response.isSuccessful) return emptyList()

            val json = JSONObject(response.body.string())
            val videoUrl = json.optString("file", "")

            if (videoUrl.isNotBlank() && videoUrl.startsWith("http")) {
                listOf(Video(videoUrl, "$serverName - 480p", videoUrl, defaultHeaders))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * TurboVIP → GET turbovidhls.com/t/{slug}
     * → ambil data-hash dari div#video_player
     */
    private fun extractTurboVip(slug: String, serverName: String): List<Video> {
        return try {
            val turboUrl = "https://turbovidhls.com/t/$slug"

            val response = client.newCall(GET(turboUrl, defaultHeaders)).execute()
            if (!response.isSuccessful) return emptyList()

            val document = response.asJsoup()
            val videoUrl = document.selectFirst("div#video_player[data-hash]")
                ?.attr("data-hash") ?: return emptyList()

            if (videoUrl.isNotBlank() && videoUrl.contains(".m3u8")) {
                listOf(Video(videoUrl, "$serverName - HLS", videoUrl, defaultHeaders))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Cast → GET f16px.com/e/{slug} → regex master.m3u8
     */
    private fun extractCast(slug: String, serverName: String): List<Video> {
        return try {
            val castUrl = "https://f16px.com/e/$slug"

            val response = client.newCall(GET(castUrl, defaultHeaders)).execute()
            if (!response.isSuccessful) return emptyList()

            val html = response.body.string()

            val m3u8Regex = """(https?://[^\s"'<>]+master\.m3u8[^\s"'<>]*)""".toRegex()
            val videoUrl = m3u8Regex.find(html)?.groupValues?.get(1) ?: return emptyList()

            if (videoUrl.isNotBlank()) {
                listOf(Video(videoUrl, "$serverName - HLS", videoUrl, defaultHeaders))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
