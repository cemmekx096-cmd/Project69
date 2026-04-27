package eu.kanade.tachiyomi.animeextension.all.erome

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import extensions.utils.getPreferencesLazy
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Erome : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Erome"
    override val baseUrl = "https://www.erome.com"
    override val lang = "all"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    // Headers untuk browse — butuh Referer dan User-Agent
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9")
        .add("Referer", "$baseUrl/")

    private val eromeExtractor by lazy { EromeExtractor(client, headers) }

    // =============================== Popular ===============================
    // Popular = Hot explore page
    override fun popularAnimeRequest(page: Int): Request {
        val url = if (page <= 1) "$baseUrl/explore" else "$baseUrl/explore?page=$page"
        return GET(url, headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("div.album").mapNotNull { it.toSAnime() }
        val hasNext = animes.isNotEmpty() &&
            doc.selectFirst(".pagination a.next") != null
        return AnimesPage(animes, hasNext)
    }

    override fun popularAnimeSelector() = "div.album"
    override fun popularAnimeFromElement(element: Element) = element.toSAnime()!!
    override fun popularAnimeNextPageSelector() = ".pagination a.next"

    // =============================== Latest ================================
    // Latest = New explore page
    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page <= 1) "$baseUrl/explore/new" else "$baseUrl/explore/new?page=$page"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)

    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // =============================== Search ================================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return GET("$baseUrl/search?q=$query&page=$page", headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("div#albums > div").mapNotNull { element ->
            // Filter: skip album yang tidak punya video
            val rawCount = element.selectFirst("span.album-videos")?.text()?.trim().orEmpty()
            val videoCount = parseVideoCount(rawCount)
            if (videoCount == 0) return@mapNotNull null
            element.toSAnime()
        }
        val hasNext = doc.selectFirst(".pagination a.next") != null || animes.isNotEmpty()
        return AnimesPage(animes, hasNext)
    }

    override fun searchAnimeSelector() = "div#albums > div"
    override fun searchAnimeFromElement(element: Element) = element.toSAnime()!!
    override fun searchAnimeNextPageSelector() = ".pagination a.next"

    // ============================= Details =================================
    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1")?.text()?.trim().orEmpty()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            author = document.selectFirst("a#user_name")?.text()?.trim()
            genre = document.select("p.mt-10 a")
                .joinToString(", ") { it.text().trim().replace(Regex("^#+\\s*"), "") }
                .let { if (it.isBlank()) "+18" else "$it, +18" }
            status = SAnime.COMPLETED
        }
    }

    // ============================= Episodes ================================
    // Setiap video dalam album = 1 episode
    override fun episodeListParse(response: Response): List<SEpisode> {
        val doc = response.asJsoup()
        val videos = doc.select("div.video video")

        if (videos.isEmpty()) {
            // Tidak ada video di halaman, kembalikan 1 episode dummy dengan URL album
            return listOf(
                SEpisode.create().apply {
                    setUrlWithoutDomain(response.request.url.toString())
                    name = "Episode 1"
                    episode_number = 1F
                },
            )
        }

        return videos.mapIndexedNotNull { idx, videoTag ->
            val src = videoTag.attr("src").ifBlank {
                videoTag.selectFirst("source")?.attr("src") ?: ""
            }.trim()
            if (src.isBlank()) return@mapIndexedNotNull null

            val label = source.attr("label").ifBlank {
                inferQualityFromUrl(src) ?: "Video ${idx + 1}"
            }

            SEpisode.create().apply {
                // Simpan src langsung sebagai URL episode
                url = src
                name = label
                episode_number = (idx + 1).toFloat()
            }
        }
    }

    override fun episodeListSelector() = "div.video video"

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ============================= Video Links =============================
    override fun videoListParse(response: Response): List<Video> {
        val url = response.request.url.toString()

        // Kalau URL langsung mp4/m3u8 → return langsung
        if (url.contains(".mp4") || url.contains(".m3u8")) {
            val quality = inferQualityFromUrl(url) ?: "HD"
            val playHeaders = headers.newBuilder()
                .set("Referer", "$baseUrl/")
                .build()
            return listOf(Video(url, quality, url, playHeaders))
        }

        // Kalau URL album → parse video dari halaman
        return eromeExtractor.videosFromDocument(response.asJsoup())
    }

    override fun videoListSelector() = "div.video video source"
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val videoQualityPref = ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = PREF_QUALITY_TITLE
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_ENTRIES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }
        screen.addPreference(videoQualityPref)
    }

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
        return sortedWith(compareByDescending { it.quality.contains(quality) })
    }

    // ============================= Utilities ==============================
    private fun Element.toSAnime(): SAnime? {
        val titleElement = selectFirst("a.album-title") ?: return null
        val title = titleElement.text().trim()
        val href = titleElement.attr("href").takeIf { it.isNotBlank() } ?: return null

        // Filter: skip album tanpa video
        val rawCount = selectFirst("span.album-videos")?.text()?.trim().orEmpty()
        if (parseVideoCount(rawCount) == 0) return null

        return SAnime.create().apply {
            setUrlWithoutDomain(href)
            this.title = title
            thumbnail_url = selectFirst("img.album-thumbnail.active")?.attr("src")
                ?: selectFirst("a.album-link img.active")?.attr("src")
        }
    }

    private fun parseVideoCount(raw: String): Int {
        val numeric = raw.replace(Regex("[^0-9KMkm]"), "")
        return when {
            numeric.isBlank() -> 0
            numeric.contains(Regex("[Kk]")) ->
                (numeric.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0) * 1000
            numeric.contains(Regex("[Mm]")) ->
                (numeric.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0) * 1_000_000
            else -> numeric.toIntOrNull() ?: 0
        }
    }

    private fun inferQualityFromUrl(url: String): String? {
        return Regex("_(\\d{3,4}p)\\.(mp4|m3u8)$", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)?.uppercase()
    }

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Preferred quality"
        private const val PREF_QUALITY_DEFAULT = "720P"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080P", "720P", "480P", "360P", "HD")
    }
}
