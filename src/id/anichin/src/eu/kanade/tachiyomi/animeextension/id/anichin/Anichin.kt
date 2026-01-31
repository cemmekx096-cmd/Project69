package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.anichinextractor.AnichinExtractor
import eu.kanade.tachiyomi.lib.anichin2extractor.Anichin2Extractor
import eu.kanade.tachiyomi.lib.cloudflareinterceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.lib.googledriveextractor.GoogleDriveExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    private val cloudflareInterceptor by lazy { CloudflareInterceptor(network.client) }

    override val client: OkHttpClient
        get() {
            val builder = network.client.newBuilder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
            if (preferences.getBoolean(PREF_CLOUDFLARE_KEY, PREF_CLOUDFLARE_DEFAULT)) {
                builder.addInterceptor(cloudflareInterceptor)
            }
            return builder.build()
        }

    // Extractors
    private val okruExtractor by lazy { OkruExtractor(client) }
    private val googleDriveExtractor by lazy { GoogleDriveExtractor(client, headers) }
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }
    private val anichinVipExtractor by lazy { AnichinExtractor(client) }
    private val anichin2Extractor by lazy { Anichin2Extractor(client) }

    // Popular Anime
    override fun popularAnimeSelector() = "div.listupd article.bs"
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime/?page=$page&status=&type=&order=popular")
    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        title = element.select("div.tt h2").text()
        thumbnail_url = element.select("img").attr("src")
        setUrlWithoutDomain(element.select("a").attr("href"))
    }
    override fun popularAnimeNextPageSelector() = "a.next.page-numbers"

    // Latest Anime
    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/anime/?page=$page&status=&type=&order=latest")
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // Search Anime
    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$baseUrl/page/$page/".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("s", query)
        return GET(url.toString(), headers)
    }
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // Episode List
    override fun episodeListSelector() = "div.eplister ul li"
    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        val epNum = element.select("div.epl-num").text()
        name = "Episode $epNum"
        episode_number = epNum.toFloatOrNull() ?: 0f
        setUrlWithoutDomain(element.select("a").attr("href"))
    }

    // Video List (LOGIKA BARU)
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        document.select("select.mirror option").forEach { option ->
            val serverValue = option.attr("value")
            val serverName = option.text().trim()

            if (serverValue.isNotEmpty() && !serverName.contains("Select", ignoreCase = true)) {
                val decodedHtml = try {
                    String(android.util.Base64.decode(serverValue, android.util.Base64.DEFAULT))
                } catch (e: Exception) { serverValue }

                val iframeSrc = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(decodedHtml)?.groupValues?.get(1)

                if (iframeSrc != null) {
                    val cleanUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
                    extractVideoFromUrl(cleanUrl, videoList, serverName)
                }
            }
        }

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

    private fun extractVideoFromUrl(url: String, videoList: MutableList<Video>, serverName: String) {
        val finalUrl = if (url.contains("short")) followRedirect(url) else url
        when {
            finalUrl.contains("ok.ru") -> videoList.addAll(okruExtractor.videosFromUrl(finalUrl, "$serverName - "))
            finalUrl.contains("anichin") -> videoList.addAll(anichinVipExtractor.videosFromUrl(finalUrl, serverName))
            finalUrl.contains("drive.google") -> videoList.addAll(googleDriveExtractor.videosFromUrl(finalUrl, "$serverName - "))
            finalUrl.contains(".m3u8") -> videoList.addAll(playlistUtils.extractFromHls(finalUrl, referer = finalUrl))
            
            // Panggil Jembatan Anichin2 (Dailymotion, Rumble, Rubyvid)
            finalUrl.contains("rumble") || finalUrl.contains("dailymotion") || finalUrl.contains("rubyvid") -> {
                videoList.addAll(anichin2Extractor.getVideos(finalUrl, serverName))
            }
            else -> videoList.add(Video(finalUrl, serverName, finalUrl))
        }
    }

    private fun followRedirect(url: String): String {
        return try {
            client.newCall(GET(url, headers)).execute().request.url.toString()
        } catch (e: Exception) { url }
    }

    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // Anime Details
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.select("h1.entry-title").text()
        author = document.select("div.spe span:contains(Studio)").text().replace("Studio: ", "")
        status = parseStatus(document.select("div.spe span:contains(Status)").text())
        genre = document.select("div.genx span a").joinToString { it.text() }
        description = document.select("div.entry-content[itemprop=description]").text()
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SAnime.ONGOING
        status.contains("Completed") -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    // Settings & Filter (Tetap sama sesuai file aslimu)
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = "Base URL"
            setDefaultValue(PREF_BASE_URL_DEFAULT)
            summary = baseUrl
        }
        screen.addPreference(baseUrlPref)
    }

    companion object {
        private const val PREF_BASE_URL_KEY = "base_url"
        private const val PREF_BASE_URL_DEFAULT = "https://anichin.watch"
        private const val PREF_CLOUDFLARE_KEY = "cloudflare_enabled"
        private const val PREF_CLOUDFLARE_DEFAULT = true
    }
}
