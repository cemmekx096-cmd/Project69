package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.cloudflareinterceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class Anichin : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Anichin"

    override val baseUrl: String
        get() = preferences.getString(PREF_BASE_URL_KEY, PREF_BASE_URL_DEFAULT)!!

    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val cloudflareInterceptor by lazy {
        CloudflareInterceptor(network.client)
    }

    // ✨ Mempertahankan Client Dynamic kamu (Cloudflare + Timeout)
    override val client: OkHttpClient
        get() {
            val timeoutSeconds = preferences.getString(PREF_TIMEOUT_KEY, PREF_TIMEOUT_DEFAULT)!!
                .toLongOrNull() ?: 90L

            val builder = network.client.newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)

            if (preferences.getBoolean(PREF_CLOUDFLARE_KEY, PREF_CLOUDFLARE_DEFAULT)) {
                builder.addInterceptor(cloudflareInterceptor)
            }

            return builder.build()
        }

    // ✨ Mempertahankan Headers Builder asli kamu
    override fun headersBuilder(): Headers.Builder {
        val userAgent = preferences.getString(PREF_USER_AGENT_KEY, PREF_USER_AGENT_DEFAULT)!!
        return super.headersBuilder().apply {
            add("User-Agent", userAgent)
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            add("Referer", baseUrl)
        }
    }

    // ============================== Popular & Latest ===============================
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/ongoing/?page=$page", headers)
    override fun popularAnimeSelector(): String = "div.listupd article.bs"
    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        setUrlWithoutDomain(element.selectFirst("div.bsx > a")!!.attr("href"))
        thumbnail_url = element.selectFirst("div.bsx img")?.attr("src")
        title = element.selectFirst("div.bsx a")?.attr("title") ?: ""
    }
    override fun popularAnimeNextPageSelector(): String = "div.pagination a.next"
    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)
    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = if (query.isNotEmpty()) "$baseUrl/?s=$query&page=$page" else "$baseUrl/ongoing/?page=$page"
        return GET(url, headers)
    }
    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.selectFirst("h1.entry-title")?.text() ?: ""
        thumbnail_url = document.selectFirst("div.thumb img")?.attr("src")
        genre = document.select("div.genxed a").joinToString { it.text() }
        description = document.selectFirst("div.desc")?.text() ?: ""
        status = if (document.select("div.status").text().contains("Ongoing", true)) SAnime.ONGOING else SAnime.COMPLETED
    }

    // ============================== Episodes ==============================
    override fun episodeListSelector(): String = "div.eplister ul li"
    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val episodeUrl = element.selectFirst("a")!!.attr("href")
        setUrlWithoutDomain(episodeUrl)
        val rawName = element.selectFirst("span.epcur")?.text() ?: "Episode"
        name = if (rawName.length > 80) rawName.take(77) + "..." else rawName
        episode_number = rawName.filter { it.isDigit() }.toFloatOrNull() ?: 0f
        date_upload = System.currentTimeMillis()
    }

    // ============================ Video Links (FIXED) =============================
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        // Memanggil Extractor Universal yang menangani Rumble, Dailymotion, VIP, dll.
        val extractor = UniversalBase64Extractor(client)

        document.select("select.mirror option").forEach { option ->
            val serverName = option.text().trim()
            val base64Value = option.attr("value")

            if (base64Value.isNotEmpty() && !serverName.contains("Select", true)) {
                // Skip ADS sesuai preferensi
                if (preferences.getBoolean(PREF_SKIP_ADS_KEY, PREF_SKIP_ADS_DEFAULT) && serverName.contains("ADS", true)) {
                    return@forEach
                }

                try {
                    // Semua logika pusing ada di file UniversalBase64Extractor
                    val videos = extractor.extractFromBase64(base64Value, serverName)
                    videoList.addAll(videos)
                } catch (e: Exception) {
                    android.util.Log.e("Anichin", "Error pada server $serverName")
                }
            }
        }

        return videoList.ifEmpty {
            listOf(Video(response.request.url.toString(), "Open in WebView", response.request.url.toString()))
        }
    }

    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = "Base URL"
            setDefaultValue(PREF_BASE_URL_DEFAULT)
            summary = "Current: %s"
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_CLOUDFLARE_KEY
            title = "CloudFlare Bypass"
            setDefaultValue(PREF_CLOUDFLARE_DEFAULT)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SKIP_ADS_KEY
            title = "Skip [ADS] Servers"
            setDefaultValue(PREF_SKIP_ADS_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_BASE_URL_KEY = "base_url"
        private const val PREF_BASE_URL_DEFAULT = "https://anichin.watch"
        private const val PREF_USER_AGENT_KEY = "user_agent"
        private const val PREF_USER_AGENT_DEFAULT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val PREF_CLOUDFLARE_KEY = "cloudflare_enabled"
        private const val PREF_CLOUDFLARE_DEFAULT = true
        private const val PREF_TIMEOUT_KEY = "network_timeout"
        private const val PREF_TIMEOUT_DEFAULT = "90"
        private const val PREF_SKIP_ADS_KEY = "skip_ads_servers"
        private const val PREF_SKIP_ADS_DEFAULT = true
    }
}
