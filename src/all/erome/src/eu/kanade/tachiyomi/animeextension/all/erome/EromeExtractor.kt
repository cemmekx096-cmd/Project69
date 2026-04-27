package eu.kanade.tachiyomi.animeextension.all.erome

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class EromeExtractor(private val client: OkHttpClient, private val headers: Headers) {

    // Headers khusus untuk play video - butuh Referer erome.com
    private val playHeaders: Headers = headers.newBuilder()
        .set("Referer", "https://www.erome.com/")
        .build()

    /**
     * Ambil semua video dari halaman album erome.
     * Video URL sudah ada langsung di HTML via <video><source src="...">.
     */
    fun videosFromUrl(url: String): List<Video> {
        return runCatching {
            val doc = client.newCall(GET(url, headers)).execute().asJsoup()
            videosFromDocument(doc)
        }.getOrElse { emptyList() }
    }

    /**
     * Parse video langsung dari document yang sudah di-fetch.
     * Dipakai kalau document sudah ada (hemat 1 request).
     */
    fun videosFromDocument(doc: org.jsoup.nodes.Document): List<Video> {
        return doc.select("div.video video").mapIndexedNotNull { idx, videoTag ->
            val src = videoTag.attr("src").ifBlank {
                videoTag.selectFirst("source")?.attr("src") ?: ""
            }.trim()
            if (src.isBlank()) return@mapIndexedNotNull null
            val label = inferQualityFromUrl(src) ?: "Video ${idx + 1}"
            Video(src, label, src, playHeaders)
        }
    }

    private fun inferQualityFromUrl(url: String): String? {
        return Regex("_(\\d{3,4}p)\\.(mp4|m3u8)$", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)?.uppercase()
    }
}
