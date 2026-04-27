package eu.kanade.tachiyomi.animeextension.en.javrank

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient

class JavRankExtractor(
    private val client: OkHttpClient,
    private val baseHeaders: Headers,
    private val baseUrl: String,
) {

    // Headers untuk play video — butuh Referer javrank.com
    private val videoHeaders: Headers = baseHeaders.newBuilder()
        .set("Referer", "$baseUrl/")
        .build()

    /**
     * Decode fungsi f() dari JavRank:
     * 1. slice(16) → buang 16 karakter pertama
     * 2. slice(0, -16) → buang 16 karakter terakhir
     * 3. reversed()
     * 4. base64 decode → dapat m3u8 URL
     */
    private fun decodeVideoUrl(encoded: String): String {
        val sliced = encoded.drop(16).dropLast(16)
        val reversed = sliced.reversed()
        return String(Base64.decode(reversed, Base64.DEFAULT))
    }

    /**
     * Parse m3u8 URL dari halaman video JavRank.
     * Mencari pattern: file: f("...")
     */
    fun videosFromUrl(url: String): List<Video> {
        return runCatching {
            val doc = client.newCall(GET(url, baseHeaders)).execute().asJsoup()
            videosFromDocument(doc, url)
        }.getOrElse { emptyList() }
    }

    fun videosFromDocument(
        doc: org.jsoup.nodes.Document,
        pageUrl: String,
    ): List<Video> {
        return runCatching {
            // Cari pattern file: f("...") di HTML
            val html = doc.html()
            val match = Regex("""file:\s*f\("([^"]+)"\)""").find(html)
                ?: return emptyList()

            val encoded = match.groupValues[1]
            val m3u8Url = decodeVideoUrl(encoded)

            if (m3u8Url.isBlank() || !m3u8Url.startsWith("http")) return emptyList()

            // Headers dengan Referer halaman video
            val playHeaders = videoHeaders.newBuilder()
                .set("Referer", pageUrl)
                .build()

            listOf(Video(m3u8Url, "HD", m3u8Url, playHeaders))
        }.getOrElse { emptyList() }
    }
}
