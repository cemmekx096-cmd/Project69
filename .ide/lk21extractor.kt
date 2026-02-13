package eu.kanade.tachiyomi.lib.lk21extractor

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.GET
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import eu.kanade.tachiyomi.animesource.model.Video
import org.json.JSONObject

class Lk21Extractor(private val client: OkHttpClient) {

    private val proxyHost = "playeriframe.sbs"
    
    // Header wajib agar tidak kena blokir/redirect ke abyss
    private val defaultHeaders = Headers.Builder()
        .add("Referer", "https://tv8.lk21official.cc/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .build()

    fun videoFromUrl(url: String): List<Video> {
        val videoList = mutableListOf<Video>()
        
        // 1. Identifikasi Provider & Slug
        val slug = url.substringAfterLast("/")
        val provider = when {
            url.contains("hownetwork") || url.contains("/p2p/") -> "p2p"
            url.contains("short.icu") || url.contains("/hydrax/") -> "hydrax"
            url.contains("f16px.com") || url.contains("/cast/") -> "cast"
            url.contains("turbovid") || url.contains("/turbovip/") -> "turbovip"
            else -> return emptyList()
        }

        // 2. Bungkus ke Proxy playeriframe.sbs (Penemuanmu)
        val proxyUrl = "https://$proxyHost/iframe/$provider/$slug"

        try {
            if (provider == "p2p") {
                // LOGIKA KHUSUS P2P (Butuh POST api2.php)
                val p2pApi = "https://cloud.hownetwork.xyz/api2.php?id=$slug"
                val body = FormBody.Builder()
                    .add("r", "https://tv8.lk21official.cc/")
                    .add("d", "cloud.hownetwork.xyz")
                    .build()
                
                val response = client.newCall(POST(p2pApi, defaultHeaders, body)).execute()
                val json = JSONObject(response.body!!.string())
                val videoUrl = json.getString("file")
                videoList.add(Video(videoUrl, "P2P - 480p", videoUrl))
            } else {
                // LOGIKA UMUM (Cast, Turbovip, Hydrax via Regex)
                val response = client.newCall(GET(proxyUrl, defaultHeaders)).execute()
                val html = response.body!!.string()
                
                // Cari link .m3u8 atau .mp4 di dalam HTML
                val m3u8Regex = """(https?://[^\s"'<>]+(?:\.m3u8|\.mp4)[^\s"'<>]*?)""".toRegex()
                m3u8Regex.findAll(html).forEach { match ->
                    val link = match.value
                    val quality = when {
                        provider == "cast" -> "Cast Player"
                        provider == "turbovip" -> "TurboVIP"
                        else -> "Hydrax"
                    }
                    videoList.add(Video(link, quality, link, headers = defaultHeaders))
                }
            }
        } catch (e: Exception) {
            // Error handling agar satu player mati tidak bikin crash seluruh ekstensi
        }

        return videoList
    }
}
