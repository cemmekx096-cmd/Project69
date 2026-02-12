package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.lk21extractor.Lk21Extractor
import eu.kanade.tachiyomi.lib.youtubeextractor.YoutubeExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LK21Movies : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Lk21Movies"
    override val baseUrl by lazy { Lk21Preferences.getBaseUrl(preferences) }
    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences(
            "source_$id",
            0x0000,
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Lk21Preferences.setupPreferences(screen, preferences)
    }

    // Live Scraping untuk Filter (Genre, Country, Year)
    init {
        try {
            // Scrape filter dari homepage
            val response = client.newCall(GET(baseUrl)).execute()
            val document = response.asJsoup()

            // Scrape Genres
            val genresList = mutableListOf("Semua Genre")
            document.select("ul.genre-list li a").forEach {
                genresList.add(it.text())
            }
            if (genresList.size > 1) {
                Lk21Filters.genres = genresList.toTypedArray()
            }

            // Scrape Countries
            val countriesList = mutableListOf("Semua Negara")
            document.select("ul.country-list li a").forEach {
                countriesList.add(it.text())
            }
            if (countriesList.size > 1) {
                Lk21Filters.countries = countriesList.toTypedArray()
            }

            // Scrape Years
            val yearsList = mutableListOf("Semua Tahun")
            document.select("ul.year-list li a").forEach {
                yearsList.add(it.text())
            }
            if (yearsList.size > 1) {
                Lk21Filters.years = yearsList.toTypedArray()
            }
        } catch (e: Exception) {
            // Jika gagal, gunakan default
            Lk21Filters.genres = arrayOf("Semua Genre", "Action", "Comedy", "Drama", "Horror", "Romance", "Sci-Fi", "Thriller")
            Lk21Filters.countries = arrayOf("Semua Negara", "USA", "Indonesia", "Korea", "Japan", "China", "India")
            Lk21Filters.years = arrayOf("Semua Tahun", "2024", "2023", "2022", "2021", "2020")
        }
    }

    // 1. Logika Self-Healing: Mengambil data dari API GitHub
    private fun fetchApiConfig() {
        val apiUrl = Lk21Preferences.getApiUrl(preferences)
        try {
            val response = client.newCall(GET(apiUrl)).execute()
            if (response.isSuccessful) {
                // Parse JSON dari GitHub (Simulasi parsing sederhana)
                // Jika domain berubah, update PREF_BASE_URL_KEY secara otomatis
            }
        } catch (e: Exception) { /* Silent fail */ }
    }

    // 2. Populasi Konten (Home/Latest) - Global Scraping & Anti-Duplikat
    override fun popularAnimeSelector() = ".widget li.slider"
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/populer/page/$page")

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest/page/$page")

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            // Cek jika ini adalah Drama (Series), jika ya, kita beri tanda untuk dibuang
            val isDrama = element.select(".episode").isNotEmpty()
            title = element.select("article figure a").attr("itemprop", "url").attr("title")
            setUrlWithoutDomain(element.select("article figure a").attr("href"))
            thumbnail_url = element.select("picture img").attr("src")
        }
    }

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularAnimeNextPageSelector()

    // Filter Drama & Deduplikasi
    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animeList = document.select(popularAnimeSelector())
            .filter { it.select(".episode").isEmpty() } // Buang Drama
            .map { popularAnimeFromElement(it) }
            .distinctBy { it.url } // Anti-Duplikat

        val hasNextPage = document.select("a.next").isNotEmpty()
        return AnimesPage(animeList, hasNextPage)
    }

    // 3. Search & 5 Kolom Filter
    override fun getFilterList(): AnimeFilterList {
        val filters = mutableListOf<AnimeFilter<*>>(
            AnimeFilter.Header("Gunakan filter untuk pencarian lebih spesifik"),
            GenreFilter("Genre 1", Lk21Filters.genres),
            GenreFilter("Genre 2", Lk21Filters.genres),
            YearFilter(Lk21Filters.years),
            CountryFilter(Lk21Filters.countries),
            Lk21Filters.staticFilter,
        )
        return AnimeFilterList(filters)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search?s=$query"
        } else {
            var filterUrl = "$baseUrl/page/$page"

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        if (filter.state > 0) {
                            val genre = Lk21Filters.genres[filter.state].lowercase().replace(" ", "-")
                            filterUrl = "$baseUrl/genre/$genre/page/$page"
                        }
                    }
                    is YearFilter -> {
                        if (filter.state > 0) {
                            val year = Lk21Filters.years[filter.state]
                            filterUrl = "$baseUrl/year/$year/page/$page"
                        }
                    }
                    is CountryFilter -> {
                        if (filter.state > 0) {
                            val country = Lk21Filters.countries[filter.state].lowercase().replace(" ", "-")
                            filterUrl = "$baseUrl/country/$country/page/$page"
                        }
                    }
                    is SortFilter -> {
                        when (filter.state) {
                            1 -> filterUrl = "$baseUrl/rating/page/$page"
                            2 -> filterUrl = "$baseUrl/most-viewed/page/$page"
                            3 -> filterUrl = "$baseUrl/imdb-rating-9/page/$page"
                            4 -> filterUrl = "$baseUrl/imdb-rating-8/page/$page"
                            5 -> filterUrl = "$baseUrl/imdb-rating-7/page/$page"
                            6 -> filterUrl = "$baseUrl/quality/web-dl/page/$page"
                            7 -> filterUrl = "$baseUrl/quality/bluray/page/$page"
                        }
                    }
                    else -> {}
                }
            }

            filterUrl
        }

        return GET(url)
    }

    // --- TARUH DI SINI ---
    override fun searchAnimeSelector() = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()
    // ---------------------

    // 4. Detail Film (Sinopsis, Cast, Trailer)
    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            description = document.select("div.meta-info div.synopsis.expanded").text()
            genre = document.select("div.tag-list span.tag a[href*='/genre/']").joinToString { it.text() }
            author = document.select("div.detail p a[href*='/director/']").text()
            artist = document.select("div.detail p a[href*='/artist/']").joinToString { it.text() }
            status = SAnime.COMPLETED
        }
    }

    // 5. Episode Logic: Episode 1 = Film, Episode 2 = Trailer
    override fun episodeListSelector() = "html"

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("Not used")

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodes = mutableListOf<SEpisode>()

        // Episode 1: Full Movie
        episodes.add(
            SEpisode.create().apply {
                name = "Nonton Film Utama"
                episode_number = 1f
                setUrlWithoutDomain(response.request.url.toString())
            },
        )

        // Episode 2: Trailer (Jika ada link YouTube)
        val trailerLink = document.select("a.yt-lightbox").attr("href")
        if (trailerLink.isNotEmpty()) {
            episodes.add(
                SEpisode.create().apply {
                    name = "Video Trailer"
                    episode_number = 2f
                    url = trailerLink
                },
            )
        }
        return episodes
    }

    // 6. Video Extraction: Memanggil lib/lk21-extractor
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()
        val url = response.request.url.toString()

        if (url.contains("youtube.com")) {
            return YoutubeExtractor(client).videoFromUrl(url)
        }

        val mainIframe = document.select("iframe#main-player").attr("src")
        if (mainIframe.isNotEmpty()) {
            videoList.addAll(
                Lk21Extractor(client, headers).videosFromUrl(
                    mainIframe,
                    "P2P",
                ),
            )
        }

        document.select("ul#player-list li a").forEach { server ->
            val serverName = server.attr("data-server")
            val iframeUrl = server.attr("data-url")
            videoList.addAll(
                Lk21Extractor(client, headers).videosFromUrl(
                    iframeUrl,
                    serverName,
                ),
            )
        }

        return videoList.sortVideos()
    }

    private fun List<Video>.sortVideos(): List<Video> {
        // Pastikan memanggil fungsi dari Lk21Preferences
        val preferredQuality = Lk21Preferences.getPreferredQuality(preferences)

        return this.sortedWith(
            compareByDescending { it.quality.contains(preferredQuality, ignoreCase = true) },
        )
    }

    override fun popularAnimeNextPageSelector(): String = "a.next"

    override fun videoListSelector(): String = throw Exception("Not used")

    override fun videoFromElement(element: Element): Video = throw Exception("Not used")

    override fun videoUrlParse(document: Document): String = throw Exception("Not used")
}
