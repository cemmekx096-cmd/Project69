package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Anichin : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Anichin"
    override val baseUrl = "https://anichin.cafe"
    override val lang = "id"
    override val supportsLatest = true

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/page/$page/", headers)

    override fun popularAnimeSelector(): String = "div.bs div.bsx"

    override fun popularAnimeFromElement(element: Element) = SAnime.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst("div.tt")?.text()
            ?: element.selectFirst("h2, h3, h4")?.text()
            ?: "Unknown"
        thumbnail_url = element.selectFirst("img")?.attr("src")
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a.next"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/page/$page/", headers)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String =
        popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$baseUrl/page/$page/?s=$query"
        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime =
        popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String =
        popularAnimeNextPageSelector()

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document) = SAnime.create().apply {
        title = document.selectFirst("h1.entry-title")?.text() ?: "Unknown"

        thumbnail_url = document.selectFirst("div.thumb img")?.attr("src")

        genre = document.select("div.genxed a").joinToString { it.text() }

        status = parseStatus(
            document.selectFirst("div.spe span:contains(Status)")
                ?.parent()?.ownText() ?: "",
        )

        description = document.selectFirst("div.entry-content p")?.text()
            ?: document.selectFirst("div.sinopsis")?.text()
    }

    private fun parseStatus(statusString: String): Int = when {
        statusString.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
        statusString.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector(): String =
        "div.eplister ul li, div.episodelist ul li"

    override fun episodeFromElement(element: Element) = SEpisode.create().apply {
        val link = element.selectFirst("a")!!
        setUrlWithoutDomain(link.attr("href"))

        name = link.selectFirst("div.epl-title")?.text()
            ?: link.text()
            ?: "Episode"

        episode_number = extractEpisodeNumber(name, link.attr("href"))
        date_upload = System.currentTimeMillis()
    }

    private fun extractEpisodeNumber(title: String, url: String): Float {
        val titleMatch = Regex("""episode[- ]?(\d+)""", RegexOption.IGNORE_CASE).find(title)
        if (titleMatch != null) {
            return titleMatch.groupValues[1].toFloatOrNull() ?: 1f
        }

        val urlMatch = Regex("""episode[- ]?(\d+)""", RegexOption.IGNORE_CASE).find(url)
        if (urlMatch != null) {
            return urlMatch.groupValues[1].toFloatOrNull() ?: 1f
        }

        return 1f
    }

    // ============================ Video Links =============================

    override fun videoListSelector(): String = "iframe[src*=anichin]"

    override fun videoFromElement(element: Element): Video {
        val iframeUrl = element.attr("src")
        return Video(
            url = iframeUrl,
            quality = "Default",
            videoUrl = iframeUrl,
        )
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videos = mutableListOf<Video>()

        document.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("src")

            when {
                "anichin.stream" in src -> {
                    videos.add(Video(src, "Anichin Stream", src))
                }
                else -> {
                    videos.add(Video(src, "Unknown Host", src))
                }
            }
        }

        return videos
    }

    // ============================= Utilities ==============================

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", "720p")
        return sortedWith(
            compareBy { it.quality.contains(quality ?: "720p") },
        ).reversed()
    }

    // =========================== Preferences ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // TODO: Add preferences (quality selection, etc)
    }
}
