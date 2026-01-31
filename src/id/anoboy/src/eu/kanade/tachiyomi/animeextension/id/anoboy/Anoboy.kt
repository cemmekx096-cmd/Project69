package eu.kanade.tachiyomi.animeextension.id.anoboy

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.anoboyextractor.AnoboyExtractor
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

class Anoboy : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Anoboy"
    override val baseUrl = "https://anoboy.be"
    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient = network.client
    private val anoboyExtractor by lazy { AnoboyExtractor(client) }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        add("Referer", baseUrl)
    }

    // Popular
    override fun popularAnimeSelector() = "div.column-content a.entry-link"
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)
    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        title = element.select("h3.entry-title").text()
        thumbnail_url = element.select("img").attr("src")
        setUrlWithoutDomain(element.attr("href"))
    }
    override fun popularAnimeNextPageSelector() = "a.next.page-numbers"

    // Latest
    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // Search
    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET("$baseUrl/page/$page/?s=$query", headers)
    }
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // Episode
    override fun episodeListSelector() = "div.column-content a.entry-link"
    override fun episodeFromElement(element: Element): SEpisode = SEpisode.create().apply {
        name = element.select("h3.entry-title").text()
        setUrlWithoutDomain(element.attr("href"))
    }

    // Video List (LOGIKA BARU)
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        // 1. Ambil video lewat Extractor (Base64/Kotakanime)
        videoList.addAll(anoboyExtractor.videosFromUrl(response.request.url.toString()))

        // 2. Fallback jika ada Iframe Blogger mentah
        if (videoList.isEmpty()) {
            document.select("iframe[src*='blogger.com']").forEach { iframe ->
                videoList.add(Video(iframe.attr("src"), "Blogger Direct", iframe.attr("src")))
            }
        }

        // Priority Sorting: 720p > 480p > 360p
        return videoList.sortedWith(
            compareByDescending<Video> {
                val q = it.quality.lowercase()
                when {
                    q.contains("720p") -> 10
                    q.contains("480p") -> 9
                    q.contains("360p") -> 8
                    else -> 0
                }
            }
        )
    }


    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // Details
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.select("h1.entry-title").text()
        description = document.select("div.entry-content").text()
        genre = document.select("span.cat-links a").joinToString { it.text() }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val qualityPref = ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred Quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080p", "720p", "480p", "360p")
            setDefaultValue("720p")
            summary = "%s"
        }
        screen.addPreference(qualityPref)
    }
}
