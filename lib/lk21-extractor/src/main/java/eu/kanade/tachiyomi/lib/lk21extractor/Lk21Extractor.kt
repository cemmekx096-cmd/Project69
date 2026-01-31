package eu.kanade.tachiyomi.lib.lk21extractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class Lk21Extractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String, name: String = "LK21"): List<Video> {
        val videoList = mutableListOf<Video>()
        val headers = Headers.Builder().add("Referer", "https://lk21.party/").build()

        return try {
            val document = client.newCall(GET(url, headers)).execute().asJsoup()
            document.select("select#player-select option").forEach {
                val playerUrl = it.attr("value")
                val serverName = it.attr("data-server").uppercase()
                if (playerUrl.isNotEmpty()) {
                    videoList.add(Video(playerUrl, "$name - $serverName", playerUrl, headers = headers))
                }
            }
            // Sorting 720p > 480p > Sisanya
            sortVideos(videoList)
        } catch (e: Exception) { emptyList() }
    }

    private fun sortVideos(list: List<Video>): List<Video> {
        return list.sortedWith(compareByDescending<Video> {
            val q = it.quality.lowercase()
            when {
                q.contains("720p") -> 10
                q.contains("480p") -> 9
                q.contains("360p") -> 8
                q.contains("1080p") -> 5
                else -> 0
            }
        })
    }
}
