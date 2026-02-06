package eu.kanade.tachiyomi.animeextension.id.lk21

import eu.kanade.tachiyomi.animesource.model.*
import eu.kanade.tachiyomi.animesource.online.ParsedHttpSource
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Extractor
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Preferences
import eu.kanade.tachiyomi.lib.lk21extractor.LK21Filters
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import androidx.preference.PreferenceScreen

class LK21p : ParsedHttpSource(), ConfigurableAnimeSource {

    override val name = "LK21 Movies"
    override val baseUrl = "https://d21.team" 
    override val lang = "id"
    override val supportsLatest = true

    private val extractor by lazy { Lk21Extractor(client) }
    private val helperFilters = LK21Filters()
    private var actualBaseUrl: String? = null
    
    // Penampung Filter Dinamis
    private var genresList: List<Pair<String, String>> = emptyList()
    private var countriesList: List<Pair<String, String>> = emptyList()
    private var yearsList: List<String> = emptyList()

    private fun getActualBaseUrl(): String {
        val prefUrl = Lk21Preferences.getBaseUrl(preferences, Lk21Preferences.DEFAULT_BASE_URL_MOVIES)
        if (prefUrl != Lk21Preferences.DEFAULT_BASE_URL_MOVIES) return prefUrl
        if (actualBaseUrl != null) return actualBaseUrl!!
        
        return try {
            val doc = client.newCall(GET(baseUrl)).execute().asJsoup()
            actualBaseUrl = doc.select("a.cta-button.green-button").attr("abs:href")
            actualBaseUrl!!.ifEmpty { Lk21Preferences.DEFAULT_BASE_URL_MOVIES }
        } catch (e: Exception) { Lk21Preferences.DEFAULT_BASE_URL_MOVIES }
    }

    override fun popularAnimeRequest(page: Int): Request = GET("${getActualBaseUrl()}/populer/page/$page", headers)

    override fun popularAnimeSelector() = "div.gallery-grid article, div.widget article"

    override fun popularAnimeFromElement(element: Element): SAnime? {
        // ANTI-DRAMA FILTER
        if (element.select("span.episode.complete").isNotEmpty()) return null

        return SAnime.create().apply {
            val link = element.select("figure a")
            title = link.attr("title").replace("Nonton movie ", "", true).trim()
            setUrlWithoutDomain(link.attr("href"))
            thumbnail_url = element.select("picture img").attr("abs:src")
        }
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animeList = document.select(popularAnimeSelector()).mapNotNull { popularAnimeFromElement(it) }
        return AnimesPage(animeList, document.select("a.next").isNotEmpty())
    }

    override fun animeDetailsParse(document: Document): SAnime = SAnime.create().apply {
        val info = document.select("div.movie-info")
        title = info.select("h1").text()
        genre = document.select("div.tag-list span.tag a[href*=/genre/]").joinToString { it.text() }
        description = document.select("div.synopsis").text()
        status = SAnime.COMPLETED
    }

    override fun videoListParse(response: Response): List<Video> {
        return extractor.videosFromUrl(response.request.url.toString(), name)
    }

    // DYNAMIC FILTER SCRAPING
    private fun fetchFilters() {
        if (genresList.isNotEmpty()) return
        try {
            val doc = client.newCall(GET(getActualBaseUrl(), headers)).execute().asJsoup()
            genresList = doc.select("ul.sub-menu a[href*=/genre/]").map { Pair(it.text(), it.attr("href").removeSuffix("/").substringAfterLast("/")) }
            countriesList = doc.select("ul.sub-menu a[href*=/country/]").map { Pair(it.text(), it.attr("href").removeSuffix("/").substringAfterLast("/")) }
            yearsList = doc.select("ul.sub-menu a[href*=/year/]").map { it.text() }
        } catch (e: Exception) { }
    }

    override fun getFilterList(): AnimeFilterList {
        fetchFilters()
        return helperFilters.getFilterList(genresList, countriesList, yearsList)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Lk21Preferences.setupPreferences(screen, preferences, Lk21Preferences.DEFAULT_BASE_URL_MOVIES)
    }

    // Boilerplate sisanya (Wajib ada)
    override fun episodeListSelector() = "html"
    override fun episodeFromElement(element: Element) = SEpisode.create().apply { name = "Movie"; setUrlWithoutDomain(element.baseUri()) }
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector() = "a.next"
    override fun latestUpdatesRequest(page: Int) = popularAnimeRequest(page)
    override fun popularAnimeNextPageSelector() = "a.next"
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = "a.next"
    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = GET("${getActualBaseUrl()}/?s=$query", headers)
}
