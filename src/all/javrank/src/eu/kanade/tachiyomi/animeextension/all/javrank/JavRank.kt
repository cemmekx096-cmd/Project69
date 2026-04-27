package eu.kanade.tachiyomi.animeextension.all.javrank

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
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

class JavRank : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "JavRank"
    override val baseUrl = "https://javrank.com"
    override val lang = "all"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.9")
        .add("Referer", "$baseUrl/")

    private val extractor by lazy { JavRankExtractor(client, headers, baseUrl) }

    // =============================== Popular ===============================
    // Popular = Korean JB category (most viewed)
    override fun popularAnimeRequest(page: Int): Request =
        GET("$baseUrl/category/korean-bj?sort=views&page=$page", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val doc = response.asJsoup()
        val animes = doc.select("div.item article.movie-item").map { it.toSAnime() }
        val hasNext = doc.selectFirst("a.page-link[href*='page=']:contains(Next)") != null
        return AnimesPage(animes, hasNext)
    }

    override fun popularAnimeSelector() = "div.item article.movie-item"
    override fun popularAnimeFromElement(element: Element) = element.toSAnime()
    override fun popularAnimeNextPageSelector() = "a.page-link[href*='page=']:contains(Next)"

    // =============================== Latest ================================
    // Latest = Korean Porn most recent
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/category/korean-porn?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)
    override fun latestUpdatesSelector() = popularAnimeSelector()
    override fun latestUpdatesFromElement(element: Element) = popularAnimeFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularAnimeNextPageSelector()

    // =============================== Search ================================
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val categoryFilter = filterList.find { it is CategoryFilter } as CategoryFilter

        return when {
            query.isNotBlank() ->
                GET("$baseUrl/search?type=videos&search=$query&page=$page", headers)
            categoryFilter.state != 0 ->
                GET("$baseUrl/category/${categoryFilter.toUriPart()}?page=$page", headers)
            else ->
                GET("$baseUrl/category/korean-porn?page=$page", headers)
        }
    }

    override fun searchAnimeParse(response: Response) = popularAnimeParse(response)
    override fun searchAnimeSelector() = popularAnimeSelector()
    override fun searchAnimeFromElement(element: Element) = popularAnimeFromElement(element)
    override fun searchAnimeNextPageSelector() = popularAnimeNextPageSelector()

    // ============================= Details =================================
    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("li.breadcrumb-item.active")?.text()?.trim()
                ?: document.title().replace(" - JavRank", "").trim()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            genre = document.select("div.movie-tags a, div.movie-categories a")
                .joinToString(", ") { it.text().trim() }
                .ifBlank { "Korean" }
            author = document.selectFirst("div.movie-models a, a.model-name")?.text()?.trim()
            status = SAnime.COMPLETED
            description = buildString {
                document.selectFirst("span.view")?.text()?.trim()?.also {
                    append("Views: $it\n")
                }
                document.selectFirst("span.date")?.text()?.trim()?.also {
                    append("Date: $it\n")
                }
            }.trim()
        }
    }

    // ============================= Episodes ================================
    // JavRank = 1 video per halaman, jadi 1 episode saja
    override fun episodeListParse(response: Response): List<SEpisode> {
        return listOf(
            SEpisode.create().apply {
                setUrlWithoutDomain(response.request.url.toString())
                name = "Episode 1"
                episode_number = 1F
            },
        )
    }

    override fun episodeListSelector() = throw UnsupportedOperationException()
    override fun episodeFromElement(element: Element) = throw UnsupportedOperationException()

    // ============================= Video Links =============================
    override fun videoListParse(response: Response): List<Video> {
        val doc = response.asJsoup()
        val pageUrl = response.request.url.toString()
        return extractor.videosFromDocument(doc, pageUrl)
    }

    override fun videoListSelector() = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element) = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Text search ignores filters"),
        CategoryFilter(),
    )

    private class CategoryFilter : UriPartFilter(
        "Category",
        arrayOf(
            Pair("<select>", ""),
            Pair("Korean Porn", "korean-porn"),
            Pair("Korean JB", "korean-jb"),
            Pair("Korean BJ", "korean-bj"),
            Pair("Korean Couple", "korean-couple"),
            Pair("Korean Amateur", "korean-amateur"),
            Pair("Korean Massage", "korean-massage"),
            Pair("Korean Teen", "korean-teen"),
            Pair("Korean Webcam", "korean-webcam"),
            Pair("Korean Anal", "korean-anal"),
            Pair("Korean Blowjob", "korean-blowjob"),
            Pair("Korean Lesbian", "korean-lesbian"),
            Pair("Korean Office", "korean-office"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val qualityPref = ListPreference(screen.context).apply {
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
        screen.addPreference(qualityPref)
    }

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
        return sortedWith(compareByDescending { it.quality.contains(quality) })
    }

    // ============================= Utilities ==============================
    private fun Element.toSAnime(): SAnime {
        return SAnime.create().apply {
            val link = selectFirst("a.thumbnail-container")
                ?: selectFirst("a[href]")!!
            setUrlWithoutDomain(link.attr("href"))

            title = selectFirst("h3.movie-link a, h3 a.movie-link")?.text()?.trim()
                ?: selectFirst("div.movie-name")?.text()?.trim()
                ?: "Unknown"

            thumbnail_url = selectFirst("img.main-image")?.attr("src")
                ?: selectFirst("img[loading=lazy]")?.attr("src")
        }
    }

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Preferred quality"
        private const val PREF_QUALITY_DEFAULT = "HD"
        private val PREF_QUALITY_ENTRIES = arrayOf("HD")
    }
}
