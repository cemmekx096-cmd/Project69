package eu.kanade.tachiyomi.animeextension.id.lk21

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Extractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class Lk21 : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "LK21"
    override val lang = "id"
    override val supportsLatest = true

    // Base URL Utama (Movie)
    override val baseUrl: String
        get() = preferences.getString(PREF_BASE_URL_KEY, PREF_BASE_URL_DEFAULT)!!

    // Base URL Drama (Series) - Menggunakan link yang kamu berikan
    private val dramaUrl: String
        get() = preferences.getString(PREF_DRAMA_URL_KEY, PREF_DRAMA_URL_DEFAULT)!!

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val lk21Extractor by lazy { Lk21Extractor(client) }

    // =========================== Catalog (Cari di Home) ===========================
    override fun popularAnimeSelector() = "article.f-item, div.grid-archive li"
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/populer/page/$page/", headers)
    override fun popularAnimeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))
        title = element.select("h2, h3").text().trim()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest/page/$page/", headers)
    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector() = "a.next"

    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET("$baseUrl/search/$query/page/$page/", headers)
    }
    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = "a.next"

    // =========================== Episode (Handle Redirect ke Nontondrama) ===========================
    override fun episodeListParse(response: Response): List<SEpisode> {
        var document = response.asJsoup()
        val currentUrl = response.request.url.toString()
        val episodes = mutableListOf<SEpisode>()

        // ✨ LOGIKA BYPASS REDIRECT KE WEB DRAMA ✨
        // Jika halaman berisi tombol redirect atau link ke nontondrama
        val redirectLink = document.selectFirst("a[href*=$dramaUrl], a:contains(Buka Sekarang)")
        if (redirectLink != null) {
            val realDramaUrl = redirectLink.attr("abs:href")
            document = client.newCall(GET(realDramaUrl, headers)).execute().asJsoup()
        }

        val episodeElements = document.select("ul.list-episode li, .episode-list a")

        if (episodeElements.isEmpty()) {
            // Jika Movie
            episodes.add(SEpisode.create().apply {
                name = "Movie"
                episode_number = 1f
                setUrlWithoutDomain(document.location())
            })
        } else {
            // Jika Drama
            episodeElements.forEachIndexed { index, element ->
                episodes.add(SEpisode.create().apply {
                    val epText = element.text()
                    name = if (epText.contains("Episode", true)) epText else "Episode $epText"
                    episode_number = epText.filter { it.isDigit() }.toFloatOrNull() ?: (index + 1).toFloat()
                    setUrlWithoutDomain(element.attr("abs:href"))
                })
            }
        }
        return episodes.reversed()
    }

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // =========================== Video (Bongkar Player) ===========================
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        // 1. Player Utama (Home & Drama)
        document.select("select#player-select option, ul#player_list li").forEach { el ->
            val url = el.attr("value").ifEmpty { el.attr("data-url") }
            if (url.isNotEmpty() && url.startsWith("http")) {
                videoList.addAll(lk21Extractor.videosFromUrl(url, el.text()))
            }
        }

        // 2. Button Download (Web Ke-4)
        document.select("a.btn-download").forEach { btn ->
            val url = btn.attr("abs:href")
            videoList.addAll(lk21Extractor.videosFromUrl(url, "Download Server"))
        }

        return videoList.sortedByDescending { it.quality }
    }

    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // =========================== Detail & Preferences ===========================
    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        title = document.select("h1.entry-title").text().trim()
        description = document.select(".entry-content, .synopsis, .description").text().trim()
        genre = document.select(".genre-info a, .cat-links a").joinToString { it.text() }
        thumbnail_url = document.select("picture img, .thumb img").attr("abs:src")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = "Base URL Home (Movie)"
            setDefaultValue(PREF_BASE_URL_DEFAULT)
            summary = baseUrl
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_DRAMA_URL_KEY
            title = "Base URL Drama (Nontondrama)"
            setDefaultValue(PREF_DRAMA_URL_DEFAULT)
            summary = dramaUrl
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_BASE_URL_KEY = "base_url"
        private const val PREF_BASE_URL_DEFAULT = "https://tv7.lk21official.cc"

        private const val PREF_DRAMA_URL_KEY = "drama_url"
        private const val PREF_DRAMA_URL_DEFAULT = "https://tv3.nontondrama.my"
    }
}
