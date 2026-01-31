package eu.kanade.tachiyomi.lib.anichin2extractor 

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.OkHttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray

class Anichin2Extractor(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    fun getVideos(url: String, server: String): List<Video> {
        val videoList = mutableListOf<Video>()
        try {
            when {
                // DAILYMOTION
                url.contains("dailymotion") -> {
                    val id = url.substringAfterLast("/").substringBefore("?")
                    val response = client.newCall(GET("https://www.dailymotion.com/player/metadata/video/$id")).execute().body.string()
                    val qualities = json.parseToJsonElement(response).jsonObject["qualities"]?.jsonObject
                    
                    qualities?.forEach { (quality, sources) ->
                        sources.jsonArray.forEach { source ->
                            val videoUrl = source.jsonObject["url"]?.toString()?.removeSurrounding("\"")
                            if (videoUrl != null && videoUrl.contains(".m3u8")) {
                                videoList.add(Video(videoUrl, "$server - Dailymotion ${quality}p", videoUrl))
                            }
                        }
                    }
                }

                // RUMBLE
                url.contains("rumble") -> {
                    val html = client.newCall(GET(url)).execute().body.string()
                    val mp4Regex = Regex("""\"ua\":\{\"mp4\":\{(.*?)\}""").find(html)
                    mp4Regex?.let { match ->
                        Regex("""\"([^\"]+)\":\{\"url\":\"([^\"]+)\"""").findAll(match.groupValues[1]).forEach { linkMatch ->
                            val quality = linkMatch.groupValues[1]
                            val videoUrl = linkMatch.groupValues[2].replace("\\/", "/")
                            videoList.add(Video(videoUrl, "$server - Rumble ${quality}p", videoUrl))
                        }
                    }
                }

                // RUBYVID (Pake JsUnpacker yang ada di folder yang sama)
                url.contains("rubyvid") || url.contains("vtube") -> {
                    val html = client.newCall(GET(url)).execute().body.string()
                    if (html.contains("eval(function(p,a,c,k,e,d)")) {
                        val unpacked = JsUnpacker.unpack(html)
                        val videoUrl = Regex("""file:\s*"([^"]+)"""").find(unpacked)?.groupValues?.get(1)
                        if (videoUrl != null) {
                            videoList.add(Video(videoUrl, "$server - Rubyvid", videoUrl))
                        }
                    }
                }
            }
        } catch (e: Exception) { }

        // PRIORITAS SORTING: 720p > 480p > 360p > 1080p > Sisanya
        return videoList.sortedWith(compareByDescending<Video> {
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
