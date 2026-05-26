package eu.kanade.tachiyomi.animeextension.id.universalplayer.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class VidvfExtractor(private val client: OkHttpClient) : AnimeExtractor {

    override val supportedDomains = listOf("vidvf.com")

    override suspend fun getVideoList(url: String, client: OkHttpClient): List<Video> {
        val videoId = url.substringAfterLast("/")
        val baseUrl = "https://vidvf.com"

        // Step 1: POST /token911 → dapat accessToken
        val tokenBody = FormBody.Builder()
            .add("id", videoId)
            .build()
        val tokenRequest = Request.Builder()
            .url("$baseUrl/token911")
            .post(tokenBody)
            .header("Referer", "$baseUrl/d/$videoId")
            .build()
        val tokenResponse = client.newCall(tokenRequest).execute()
        val tokenJson = JSONObject(tokenResponse.body.string())
        val accessToken = tokenJson.optString("token", "")

        // Step 2: Konversi videoId → hexId
        val hexId = videoIdToHex(videoId)

        // Step 3: GET /ip129jk?id={hexId} → dapat iframe src
        val iframeRequest = Request.Builder()
            .url("$baseUrl/ip129jk?id=$hexId")
            .header("Referer", "$baseUrl/d/$videoId")
            .header("Cookie", "accessToken=$accessToken")
            .build()
        val iframeResponse = client.newCall(iframeRequest).execute()
        val iframeHtml = iframeResponse.body.string()

        // Parse embed URL dari iframe src
        val embedUrl = Regex("""embed\.php\?bucket=temporary&(?:amp;)?id=([\w]+)""")
            .find(iframeHtml)
            ?.groupValues?.get(1)
            ?: return emptyList()

        // Step 4: GET /embed.php?bucket=temporary&id={videoId}
        val embedRequest = Request.Builder()
            .url("$baseUrl/embed.php?bucket=temporary&id=$embedUrl")
            .header("Referer", "$baseUrl/ip129jk?id=$hexId")
            .header("Cookie", "accessToken=$accessToken")
            .build()
        val embedResponse = client.newCall(embedRequest).execute()
        val embedHtml = embedResponse.body.string()

        // Step 5: Parse <source src="https://m.imgvdy.com/...">
        val videoUrl = Regex("""<source\s+src="(https://m\.imgvdy\.com/[^"]+)"""")
            .find(embedHtml)
            ?.groupValues?.get(1)
            ?: return emptyList()

        return listOf(Video(videoUrl, "vidvf [MP4]", videoUrl))
    }

    // "d20jfqna0zvy" → reverse → "yvz0anqfj02d" → hex → "79767a30616e71666a303264"
    private fun videoIdToHex(id: String): String {
        return id.reversed()
            .map { it.code.toString(16).padStart(2, '0') }
            .joinToString("")
    }
}
