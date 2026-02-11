package eu.kanade.tachiyomi.lib.youtubeextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class YoutubeExtractor(private val client: OkHttpClient) {

    /**
     * Mengekstrak link video dari URL YouTube
     * Digunakan untuk Trailer di lk21movies
     */
    fun videoFromUrl(url: String, quality: String = "YouTube Trailer"): List<Video> {
        val videoList = mutableListOf<Video>()
        
        try {
            val response = client.newCall(GET(url)).execute()
            val html = response.body.string()

            // Mencari pola URL stream di dalam script ytplayer.config
            val urlPattern = """https?://[^\s"'<>]+googlevideo\.com/[^\s"'<>]+""".toRegex()
            urlPattern.findAll(html).forEach { match ->
                var streamUrl = match.value
                    .replace("\\u0026", "&")
                    .replace("\\/", "/")
                
                // Membersihkan URL jika ada karakter sampah di akhir
                if (streamUrl.contains("&")) {
                    videoList.add(
                        Video(streamUrl, quality, streamUrl)
                    )
                }
            }
        } catch (e: Exception) {
            // Jika gagal, kembalikan list kosong
        }

        return videoList.distinctBy { it.url }
    }
}
