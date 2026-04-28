package eu.kanade.tachiyomi.animeextension.id.oploverz

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class OploverzExtractor(private val client: OkHttpClient, private val headers: Headers) {

    private val tracker = FeatureTracker("OploverzExtractor")

    fun videosFromStreamUrl(streamUrls: List<StreamUrl>): List<Video> {
        return streamUrls.mapNotNull { stream ->
            runCatching { extractVideo(stream) }.getOrNull()
        }.flatten()
    }

    private fun extractVideo(stream: StreamUrl): List<Video> {
        val url = stream.url
        val quality = parseQuality(stream.source)
        tracker.debug("extractVideo: source=${stream.source} url=$url quality=$quality")

        return when {
            "4meplayer" in url -> videosFrom4mePlayer(url, quality)
            "blogger.com" in url || "googlevideo" in url -> videosFromBlogger(url, quality)
            else -> {
                tracker.warn("extractVideo: no extractor for $url")
                emptyList()
            }
        }
    }

    // ========================= 4mePlayer =========================

    private fun videosFrom4mePlayer(url: String, quality: String): List<Video> {
        return runCatching {
            tracker.debug("4mePlayer: fetching $url")
            val doc = client.newCall(GET(url, headers)).execute().asJsoup()

            // Cari video source di script atau video tag
            val videoUrl = doc.selectFirst("video source")?.attr("src")
                ?: doc.selectFirst("video")?.attr("src")
                ?: run {
                    // Cari di script
                    val script = doc.select("script").firstOrNull {
                        it.data().contains("source") || it.data().contains("file")
                    }?.data()

                    script?.let {
                        Regex("""['"](https?://[^'"]+\.(?:mp4|m3u8)[^'"]*)['"]""")
                            .find(it)?.groupValues?.get(1)
                    }
                }

            if (videoUrl.isNullOrBlank()) {
                tracker.error("4mePlayer: video URL not found in $url")
                return emptyList()
            }

            tracker.debug("4mePlayer: videoUrl=$videoUrl")
            val playHeaders = headers.newBuilder()
                .set("Referer", "https://oplo2.4meplayer.pro/")
                .build()

            listOf(Video(videoUrl, "4mePlayer - $quality", videoUrl, playHeaders))
        }.getOrElse {
            tracker.error("4mePlayer error: ${it.message}")
            emptyList()
        }
    }

    // ========================= Blogger =========================

    private fun videosFromBlogger(url: String, quality: String): List<Video> {
        return runCatching {
            tracker.debug("Blogger: fetching $url")
            val doc = client.newCall(GET(url, headers)).execute().asJsoup()

            // Blogger embed pakai <video><source src="...">
            val videoUrl = doc.selectFirst("video source")?.attr("src")
                ?: doc.selectFirst("video")?.attr("src")

            if (videoUrl.isNullOrBlank()) {
                tracker.error("Blogger: video URL not found in $url")
                return emptyList()
            }

            tracker.debug("Blogger: videoUrl=$videoUrl")
            val playHeaders = headers.newBuilder()
                .set("Referer", "https://www.blogger.com/")
                .build()

            listOf(Video(videoUrl, "Blogger - $quality", videoUrl, playHeaders))
        }.getOrElse {
            tracker.error("Blogger error: ${it.message}")
            emptyList()
        }
    }

    // ========================= Utils =========================

    private fun parseQuality(source: String): String {
        return Regex("(\\d{3,4}p)").find(source)?.groupValues?.get(1) ?: source
    }
}
