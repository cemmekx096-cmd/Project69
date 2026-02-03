package eu.kanade.tachiyomi.animeextension.id.anichin

import android.util.Base64
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.anichin2extractor.Anichin2Extractor
import eu.kanade.tachiyomi.lib.anichinextractor.AnichinExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.network.GET
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

class UniversalBase64Extractor(private val client: OkHttpClient) {

    private val anichinVipExtractor by lazy { AnichinExtractor(client) }
    private val anichin2Extractor by lazy { Anichin2Extractor(client) }
    private val okruExtractor by lazy { OkruExtractor(client) }

    /**
     * Extract videos from URL directly (without base64 decoding)
     * Used by Anichin.kt after base64 is already decoded in main file
     */
    fun extractFromUrl(url: String, label: String): List<Video> {
        return routeToExtractor(url, label)
    }

    fun extractFromBase64(base64: String, label: String): List<Video> {
        return try {
            val decodedHtml = String(Base64.decode(base64, Base64.DEFAULT))
            val iframeSrc = Jsoup.parse(decodedHtml).select("iframe").attr("src")

            if (iframeSrc.isEmpty()) return emptyList()

            val cleanUrl = when {
                iframeSrc.startsWith("//") -> "https:$iframeSrc"
                else -> iframeSrc
            }

            extractFromUrl(cleanUrl, label)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun routeToExtractor(url: String, label: String): List<Video> {
        val videoList = mutableListOf<Video>()

        when {
            // SKIP - Already handled in Anichin.kt to avoid duplication
            url.contains("anichin") || url.contains("animichi") -> {
                android.util.Log.d("UniversalBase64", "Skipping VIP - handled by main extractor")
                return emptyList()
            }

            url.contains("ok.ru") -> {
                android.util.Log.d("UniversalBase64", "Skipping OK.ru - handled by main extractor")
                return emptyList()
            }

            // Rumble, Dailymotion, RubyVid (Gunakan logika di Anichin2Extractor)
            url.contains("rumble") || url.contains("dailymotion") || url.contains("vtube") || url.contains("rubyvid") -> {
                videoList.addAll(anichin2Extractor.videosFromUrl(url, label))
            }

            // DoodStream
            url.contains("dood") -> {
                videoList.add(Video(url, "$label - DoodStream", url))
            }

            // KrakenFiles
            url.contains("krakenfiles") -> {
                extractKraken(url, videoList, label)
            }

            // Fallback Generic (Cari link .m3u8 atau .mp4 langsung di HTML)
            else -> {
                videoList.addAll(tryGenericExtraction(url, label))
            }
        }
        return videoList
    }

    private fun extractKraken(url: String, videoList: MutableList<Video>, label: String) {
        try {
            val html = client.newCall(GET(url)).execute().body.string()
            val videoUrl = Regex("""source\s*src=["']([^"']+)["']""").find(html)?.groupValues?.get(1)
            if (videoUrl != null) {
                val fullUrl = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
                videoList.add(Video(fullUrl, "$label - Kraken", fullUrl))
            }
        } catch (e: Exception) {}
    }

    private fun tryGenericExtraction(url: String, label: String): List<Video> {
        // Logika mencari link m3u8 secara kasar jika tidak ada extractor yang cocok
        return try {
            val html = client.newCall(GET(url)).execute().body.string()
            val m3u8Regex = """(https?://[^\s"'<>]+?\.m3u8[^\s"'<>]*?)""".toRegex()
            m3u8Regex.find(html)?.groupValues?.get(1)?.let {
                listOf(Video(it, "$label - Player", it))
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
}
