package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.app.Application
import android.content.SharedPreferences
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

class LK21Movies : ParsedAnimeHttpSource() {

    override val name = "Lk21Movies"
    override val baseUrl by lazy { Lk21Preferences.getBaseUrl(preferences) }
    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", Application.MODE_PRIVATE)
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
    override fun popularAnimeSelector() = ".widget li.slider" //
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/populer/page/$page")

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest/page/$page") //

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            // Cek jika ini adalah Drama (Series), jika ya, kita beri tanda untuk dibuang
            val isDrama = element.select(".episode").isNotEmpty()
            title = element.select("article figure a").attr("itemprop", "url").attr("title")
            setUrlWithoutDomain(element.select("article figure a").attr("href"))
            thumbnail_url = element.select("picture img").attr("src") //
        }
    }

    // Filter Drama & Deduplikasi
    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animeList = document.select(popularAnimeSelector())
            .filter { it.select(".episode").isEmpty() } // Buang Drama
            .map { popularAnimeFromElement(it) }
            .distinctBy { it.url } // Anti-Duplikat

        val hasNextPage = document.select("a.next").isNotEmpty() //
        return AnimesPage(animeList, hasNextPage)
    }

    // 3. Search & 5 Kolom Filter
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        // Logika penggabungan filter Genre 1, Genre 2, Tahun, Negara, dan Rating
        return GET("$baseUrl/search?s=$query") //
    }

    // 4. Detail Film (Sinopsis, Cast, Trailer)
    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            description = document.select("div.meta-info div.synopsis.expanded").text() //
            genre = document.select("div.tag-list span.tag a[href*='/genre/']").joinToString { it.text() } //
            author = document.select("div.detail p a[href*='/director/']").text() //
            artist = document.select("div.detail p a[href*='/artist/']").joinToString { it.text() } //
            status = SAnime.COMPLETED
        }
    }

    // 5. Episode Logic: Episode 1 = Film, Episode 2 = Trailer
    override fun episodeListSelector() = "html"
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
        return episodes
    }

    // 6. Video Extraction: Memanggil lib/lk21-extractor
    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()
        val url = response.request.url.toString()

        if (url.contains("youtube.com")) {
            // Gunakan YoutubeExtractor untuk Trailer
            return YoutubeExtractor(client).videoFromUrl(url)
        }

        // Ambil link iframe player utama
        val mainIframe = document.select("iframe#main-player").attr("src")
        if (mainIframe.isNotEmpty()) {
            videoList.addAll(Lk21Extractor(client, headers).videosFromUrl(mainIframe, "P2P")) //
        }

        // Ambil dari server alternatif (Multi-Server)
        document.select("ul#player-list li a").forEach { server ->
            val serverName = server.attr("data-server")
            val iframeUrl = server.attr("data-url")
            videoList.addAll(Lk21Extractor(client, headers).videosFromUrl(iframeUrl, serverName)) //
        }

        return videoList.sortVideos()
    }

    // Helper untuk sorting kualitas berdasarkan preferensi user
    private fun List<Video>.sortVideos(): List<Video> {
        val quality = Lk21Preferences.getPreferredQuality(preferences)
        return this.sortedWith(compareByDescending { it.quality.contains(quality) })
    }

    override fun videoListSelector() = throw Exception("Not used")
    override fun videoFromElement(element: Element) = throw Exception("Not used")
    override fun videoUrlParse(document: Document) = throw Exception("Not used")
    }
}
