package eu.kanade.tachiyomi.animeextension.id.otakudesu

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class DesuStreamExtractor(private val client: OkHttpClient, private val headers: Headers) {

    fun videosFromUrl(url: String, quality: String): List<Video> {
        return runCatching {
            val doc = client.newCall(GET(url, headers)).execute().asJsoup()

            // DesuStream wraps Google Video in <video><source src="...">
            val videoUrl = doc.selectFirst("video source")?.attr("src")
                ?: return emptyList()

            listOf(Video(videoUrl, "DesuStream - $quality", videoUrl, headers))
        }.getOrElse { emptyList() }
    }
}
