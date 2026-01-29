package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.cloudflareinterceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.googledriveextractor.GoogleDriveExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.TimeUnit

class Anichin : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Anichin"
    override val baseUrl = "https://anichin.watch"
    override val lang = "id"
    override val supportsLatest = true

    private val cloudflareInterceptor by lazy {
        CloudflareInterceptor(network.client)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(cloudflareInterceptor)
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        add("Referer", baseUrl)
        add("DNT", "1")
        add("Connection", "keep-alive")
        add("Upgrade-Insecure-Requests", "1")
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val json: Json by injectLazy()

    private val okruExtractor by lazy { OkruExtractor(client) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val googleDriveExtractor by lazy { GoogleDriveExtractor(client, headers) }
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/ongoing/?page=$page", headers)

    override fun popularAnimeSelector(): String = "div.listupd article.bs"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            setUrlWithoutDomain(element.selectFirst("div.bsx > a")!!.attr("href"))
            thumbnail_url = element.selectFirst("div.bsx img")?.attr("src")
            title = element.selectFirst("div.bsx a")?.attr("title") ?: ""
        }
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a.next"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/ongoing/?page=$page", headers)
    override fun latestUpdatesSelector(): String = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = AnichinFilters.getSearchParameters(filters)
        val url = when {
            query.isNotEmpty() -> "$baseUrl/?s=$query&page=$page"
            params.genre.isNotEmpty() -> "$baseUrl/genres/${params.genre}/?page=$page"
            else -> "$baseUrl/ongoing/?page=$page"
        }
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1.entry-title")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.thumb img")?.attr("src")
            genre = document.select("div.genxed a").joinToString { it.text() }
            status = when {
                document.select("div.status").text().contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
                document.select("div.status").text().contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
            description = document.selectFirst("div.desc")?.text()
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector(): String = "div.eplister ul li"

    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            val rawName = element.selectFirst("span.epcur")?.text()
                ?: element.selectFirst("a")?.text()
                ?: "Episode"
            name = if (rawName.length > 77) rawName.take(77) + "..." else rawName
            episode_number = rawName.filter { it.isDigit() }.toFloatOrNull() ?: 0f
            date_upload = System.currentTimeMillis()
        }
    }

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        android.util.Log.d("Anichin", "=== VIDEO PARSE START ===")

        document.select("select.mirror option").forEachIndexed { index, option ->
            val serverValue = option.attr("value")
            val serverName = option.text().trim()

            if (serverValue.isNotEmpty() && !serverName.contains("Select", ignoreCase = true)) {
                try {
                    val decodedHtml = try {
                        String(android.util.Base64.decode(serverValue, android.util.Base64.DEFAULT))
                    } catch (e: Exception) {
                        serverValue
                    }

                    val iframeSrc = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(decodedHtml)
                        ?.groupValues
                        ?.get(1)

                    if (iframeSrc != null) {
                        val cleanUrl = when {
                            iframeSrc.startsWith("//") -> "https:$iframeSrc"
                            else -> iframeSrc
                        }
                        android.util.Log.d("Anichin", "[$index] $serverName: $cleanUrl")
                        extractVideoFromIframe(cleanUrl, videoList, serverName)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Anichin", "[$index] Error: ${e.message}")
                }
            }
        }

        android.util.Log.d("Anichin", "=== TOTAL: ${videoList.size} ===")
        return videoList.ifEmpty {
            listOf(Video(response.request.url.toString(), "WebView (Tap to open)", response.request.url.toString()))
        }
    }

    private fun extractVideoFromIframe(url: String, videoList: MutableList<Video>, quality: String) {
        try {
            when {
                // Anichin.stream custom player
                url.contains("anichin.stream") || url.contains("anichinv2.icu") -> {
                    extractAnichinStream(url, videoList, quality)
                }
                // OK.ru
                url.contains("ok.ru") -> {
                    val videos = okruExtractor.videosFromUrl(url, "$quality - ")
                    if (videos.isNotEmpty()) {
                        videoList.addAll(videos)
                    } else {
                        android.util.Log.w("Anichin", "$quality: OK.ru extraction failed")
                    }
                }
                // Dailymotion
                url.contains("dailymotion") -> {
                    val videos = dailymotionExtractor.videosFromUrl(url, prefix = "$quality - ")
                    if (videos.isNotEmpty()) {
                        videoList.addAll(videos)
                    } else {
                        android.util.Log.w("Anichin", "$quality: Dailymotion extraction failed")
                    }
                }
                // Google Drive
                url.contains("drive.google") -> {
                    val videos = googleDriveExtractor.videosFromUrl(url, "$quality - ")
                    if (videos.isNotEmpty()) {
                        videoList.addAll(videos)
                    } else {
                        android.util.Log.w("Anichin", "$quality: GDrive extraction failed")
                    }
                }
                // Rumble
                url.contains("rumble.com") -> {
                    extractRumble(url, videoList, quality)
                }
                else -> {
                    android.util.Log.d("Anichin", "$quality: Unknown source $url")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Anichin", "$quality extraction error: ${e.message}")
        }
    }

    private fun extractAnichinStream(url: String, videoList: MutableList<Video>, quality: String) {
        try {
            val document = client.newCall(GET(url, headers)).execute().asJsoup()

            // Method 1: Find Rumble embed in iframe
            val rumbleUrl = document.selectFirst("iframe[src*=rumble]")?.attr("src")
            if (rumbleUrl != null) {
                val cleanRumble = if (rumbleUrl.startsWith("//")) "https:$rumbleUrl" else rumbleUrl
                android.util.Log.d("Anichin", "$quality: Found Rumble in anichin.stream")
                extractRumble(cleanRumble, videoList, quality)
                return
            }

            // Method 2: Find video/source tag
            val videoUrl = document.selectFirst("video source, video")?.attr("src")
            if (!videoUrl.isNullOrEmpty()) {
                android.util.Log.d("Anichin", "$quality: Found direct video $videoUrl")
                videoList.add(Video(videoUrl, quality, videoUrl))
                return
            }

            // Method 3: Search for m3u8 in scripts
            val m3u8Url = document.select("script").mapNotNull { script ->
                Regex("""["'](https?://[^"']*\.m3u8[^"']*)["']""")
                    .find(script.data())
                    ?.groupValues
                    ?.get(1)
            }.firstOrNull()

            if (m3u8Url != null) {
                android.util.Log.d("Anichin", "$quality: Found m3u8 $m3u8Url")
                val videos = playlistUtils.extractFromHls(m3u8Url, url, videoNameGen = { "$quality - $it" })
                videoList.addAll(videos)
            } else {
                android.util.Log.w("Anichin", "$quality: No video found in anichin.stream")
            }
        } catch (e: Exception) {
            android.util.Log.e("Anichin", "$quality anichin.stream error: ${e.message}")
        }
    }

    private fun extractRumble(url: String, videoList: MutableList<Video>, quality: String) {
        try {
            val document = client.newCall(GET(url, headers)).execute().asJsoup()

            // Find m3u8 in scripts
            val m3u8Url = document.select("script").mapNotNull { script ->
                Regex("""["'](https://[^"']*\.m3u8[^"']*)["']""")
                    .findAll(script.data())
                    .map { it.groupValues[1] }
                    .firstOrNull()
            }.firstOrNull()

            if (m3u8Url != null) {
                android.util.Log.d("Anichin", "$quality: Found Rumble m3u8")
                val videos = playlistUtils.extractFromHls(m3u8Url, url, videoNameGen = { "$quality - $it" })
                videoList.addAll(videos)
            } else {
                android.util.Log.w("Anichin", "$quality: Rumble m3u8 not found")
            }
        } catch (e: Exception) {
            android.util.Log.e("Anichin", "$quality Rumble error: ${e.message}")
        }
    }

    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList() = AnimeFilterList(
        AnimeFilter.Header("NOTE: Filters ignored if using text search!"),
        AnimeFilter.Separator(),
        AnichinFilters.GenreFilter(),
    )

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
        private val PREF_QUALITY_VALUES = arrayOf("1080p", "720p", "480p", "360p")
    }
}
