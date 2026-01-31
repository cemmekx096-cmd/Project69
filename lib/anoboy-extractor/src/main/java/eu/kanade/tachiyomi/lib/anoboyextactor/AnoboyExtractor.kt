package eu.kanade.tachiyomi.lib.anoboyextractor

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class AnoboyExtractor(private val client: OkHttpClient) {
    fun videosFromUrl(url: String): List<Video> {
        val videoList = mutableListOf<Video>()
        val headers = Headers.Builder().add("Referer", "https://anoboy.icu/").build()

        return try {
            val document = client.newCall(GET(url, headers)).execute().asJsoup()
            document.select("select#select-video option, select[name='server'] option").forEach {
                val label = it.text()
                val b64 = it.attr("value")
                if (b64.isNotEmpty()) {
                    val decoded = String(Base64.decode(b64, Base64.DEFAULT))
                    val iframeUrl = Regex("""src=["']([^"']+)["']""").find(decoded)?.groupValues?.get(1)
                    if (iframeUrl != null) {
                        videoList.add(Video(iframeUrl, label, iframeUrl, headers = headers))
                    }
                }
            }
            videoList
        } catch (e: Exception) { emptyList() }
    }
}
