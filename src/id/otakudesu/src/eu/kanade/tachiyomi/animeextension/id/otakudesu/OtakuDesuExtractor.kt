package eu.kanade.tachiyomi.animeextension.id.otakudesu

import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

class OtakuDesuExtractor(
    private val client: OkHttpClient,
    private val baseHeaders: Headers,
    private val baseUrl: String,
) {
    private val tracker = FeatureTracker("OtakuDesuExtractor")

    // Header dibangun langsung di constructor — tidak pakai lazy
    private val ajaxHeaders: Headers = baseHeaders.newBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    // ===================== Nonce =====================

    fun getNonce(nonceAction: String): String {
        val form = FormBody.Builder()
            .add("action", nonceAction)
            .build()

        val raw = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, body = form))
            .execute()
            .body.string()

        tracker.debug("getNonce: raw=$raw")

        // Response format: {"data":"nonceValue"}
        val nonce = raw.substringAfter("\"data\":\"").substringBefore("\"")
        tracker.debug("getNonce: nonce=$nonce")
        return nonce
    }

    // ===================== Embed Link =====================

    fun getEmbedUrl(
        id: String,
        mirror: String,
        quality: String,
        nonce: String,
        action: String,
    ): Pair<String, String> {
        val form = FormBody.Builder().apply {
            add("id", id)
            add("i", mirror)
            add("q", quality)
            add("nonce", nonce)
            add("action", action)
        }.build()

        val raw = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, body = form))
            .execute()
            .body.string()

        tracker.debug("getEmbedUrl: raw=$raw")

        // Response format: {"data":"base64EncodedHtml"}
        val iframeHtml = raw
            .substringAfter("\"data\":\"")
            .substringBefore("\"")
            .let { String(android.util.Base64.decode(it, android.util.Base64.DEFAULT)) }

        tracker.debug("getEmbedUrl: iframeHtml=$iframeHtml")

        val url = Jsoup.parse(iframeHtml).selectFirst("iframe")?.attr("src")
            ?: run {
                tracker.error("getEmbedUrl: iframe src tidak ditemukan! html=$iframeHtml")
                throw Exception("iframe src not found")
            }

        tracker.debug("getEmbedUrl: url=$url")
        return Pair(quality, url)
    }
}
