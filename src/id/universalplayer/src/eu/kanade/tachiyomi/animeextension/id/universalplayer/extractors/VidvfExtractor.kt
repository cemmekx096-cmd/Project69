package eu.kanade.tachiyomi.animeextension.id.universalplayer.extractors

import eu.kanade.tachiyomi.animeextension.id.universalplayer.FeatureTracker
import eu.kanade.tachiyomi.animeextension.id.universalplayer.PerformanceTracker
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class VidvfExtractor(private val client: OkHttpClient) : AnimeExtractor {

    override val supportedDomains = listOf("vidvf.com", "vixoly.de")

    override suspend fun getVideoList(url: String, client: OkHttpClient): List<Video> {
        val perf = PerformanceTracker("VidvfExtractor")
        val tracker = FeatureTracker("VidvfExtractor")
        perf.start()
        tracker.start()

        val normalizedUrl = url.replace("vixoly.de", "vidvf.com")
        val videoId = normalizedUrl.substringAfterLast("/")
        val baseUrl = "https://vidvf.com"
        tracker.debug("Step 0 OK: $url → $normalizedUrl | videoId=$videoId")

        return try {
            // Step 1: GET /d/{videoId} → set cookie
            client.newCall(
                Request.Builder()
                    .url("$baseUrl/d/$videoId")
                    .header("User-Agent", UA)
                    .header("Referer", baseUrl)
                    .build(),
            ).execute().close()
            tracker.debug("Step 1 OK: halaman utama di-fetch")

            // Step 2: POST /token911 → dapat accessToken
            val tokenResponse = client.newCall(
                Request.Builder()
                    .url("$baseUrl/token911")
                    .post(FormBody.Builder().add("id", videoId).build())
                    .header("User-Agent", UA)
                    .header("Referer", "$baseUrl/d/$videoId")
                    .header("Origin", baseUrl)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build(),
            ).execute()
            val tokenJson = JSONObject(tokenResponse.body.string())
            val accessToken = tokenJson.optString("token", "")
            if (accessToken.isBlank()) {
                tracker.error("Step 2 FAILED: accessToken kosong!")
                perf.end()
                return emptyList()
            }
            tracker.debug("Step 2 OK: accessToken=$accessToken")

            // Step 3: GET /embed.php
            val embedHtml = client.newCall(
                Request.Builder()
                    .url("$baseUrl/embed.php?bucket=temporary&id=$videoId")
                    .header("User-Agent", UA)
                    .header("Referer", "$baseUrl/d/$videoId")
                    .header("Cookie", "accessToken=$accessToken")
                    .build(),
            ).execute().body.string()
            tracker.debug("Step 3 OK: embed.php di-fetch | length=${embedHtml.length}")

            if (embedHtml.isBlank()) {
                tracker.error("Step 3 FAILED: response kosong!")
                perf.end()
                return emptyList()
            }

            // Step 4: Parse video URL
            val videoUrl = Regex("""<source\s+src="(https://m\.imgvdy\.com/[^"]+)"""")
                .find(embedHtml)
                ?.groupValues?.get(1)

            if (videoUrl == null) {
                tracker.error("Step 4 FAILED: source URL tidak ditemukan")
                tracker.debug("HTML preview: ${embedHtml.take(500)}")
                perf.end()
                return emptyList()
            }

            tracker.success("Step 4 OK: videoUrl=$videoUrl")
            perf.end()
            listOf(Video(videoUrl, "vidvf [MP4]", videoUrl))
        } catch (e: Exception) {
            tracker.error("FATAL: ${e.message}")
            perf.end()
            emptyList()
        }
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Android 16; Mobile; rv:151.0) Gecko/151.0 Firefox/151.0"
    }
}
