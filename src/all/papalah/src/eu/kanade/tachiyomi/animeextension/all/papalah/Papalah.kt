package eu.kanade.tachiyomi.animeextension.all.papalah

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.all.papalah.extractors.PapalahExtractorFactory
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class Papalah : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Papalah"

    override val baseUrl = "https://www.papalah.com"

    override val lang = "id"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/hot?page=$page", headers)
    }

    override fun popularAnimeSelector(): String = "div.col-md-3.col-xs-6.item a[data-id]"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val href = element.attr("href").removePrefix(".")
            setUrlWithoutDomain(if (href.startsWith("/")) href else "/$href")
            title = element.attr("title")

            val thumb = element.selectFirst("img.v-thumb")
            thumbnail_url = thumb?.attr("data-src") ?: thumb?.attr("src")
        }
    }

    override fun popularAnimeNextPageSelector(): String = "a.page-link[rel=next]"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/?page=$page", headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            // Search by query
            GET("$baseUrl/?q=$query&page=$page", headers)
        } else {
            // Apply filters
            val tagFilter = filters.filterIsInstance<PapalahFilters.TagFilter>().firstOrNull()
            val sortFilter = filters.filterIsInstance<PapalahFilters.SortFilter>().firstOrNull()

            when {
                // Filter by tag
                tagFilter != null && !tagFilter.isEmpty() -> {
                    GET("$baseUrl/tag/${tagFilter.toUriPart()}?page=$page", headers)
                }
                // Sort filter (hot/latest)
                sortFilter != null && !sortFilter.isEmpty() -> {
                    GET("$baseUrl/${sortFilter.toUriPart()}?page=$page", headers)
                }
                // Default: latest
                else -> {
                    GET("$baseUrl/?page=$page", headers)
                }
            }
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("div.v-name")?.text() ?: ""

            thumbnail_url = document.selectFirst("div.v-thumb-outer img")?.let {
                it.attr("data-src").ifEmpty { it.attr("src") }
            }

            // Ambil tags sebagai genre
            genre = document.select("div.v-keywords a").joinToString {
                it.text()
            }

            description = buildString {
                document.selectFirst("div.v-name")?.text()?.let {
                    append("Title: $it\n\n")
                }

                document.selectFirst("span.timeago")?.attr("title")?.let {
                    append("Upload: $it\n")
                }

                document.selectFirst("span.views-text")?.text()?.let {
                    append("Views: $it\n")
                }

                document.selectFirst("div.v-duration")?.text()?.let {
                    append("Duration: $it")
                }
            }

            status = SAnime.COMPLETED
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()

        return listOf(
            SEpisode.create().apply {
                name = document.selectFirst("div.v-name")?.text() ?: "Video"
                episode_number = 1F
                date_upload = parseDate(document.selectFirst("span.timeago")?.attr("title"))
                val url = response.request.url.toString()
                setUrlWithoutDomain(url.removePrefix(baseUrl))
            },
        )
    }

    override fun episodeListSelector(): String = throw Exception("Not used")

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("Not used")

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val html = document.html()
        val factory = PapalahExtractorFactory(client, headers)

        // Extract videos dari HTML page
        return factory.extractFromHtml(html, response.request.url.toString())
    }

    override fun videoListSelector(): String = throw Exception("Not used")

    override fun videoFromElement(element: Element): Video = throw Exception("Not used")

    override fun videoUrlParse(document: Document): String = throw Exception("Not used")

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList {
        // Fetch tags dynamically (bisa juga pakai getPopularTags() untuk static list)
        val tags = try {
            PapalahFilters.fetchTagsFromPage(client, baseUrl)
        } catch (e: Exception) {
            PapalahFilters.getPopularTags()
        }

        return AnimeFilterList(
            AnimeFilter.Header("NOTE: Filter akan diabaikan jika search query tidak kosong!"),
            AnimeFilter.Separator(),
            PapalahFilters.TagFilter(tags),
            PapalahFilters.SortFilter(),
        )
    }

    // ============================= Utilities ==============================

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0L

        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            format.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override fun List<Video>.sort(): List<Video> {
        val quality = preferences.getString("preferred_quality", "1080")!!

        return sortedWith(
            compareBy(
                { it.quality.contains(quality) },
                { Regex("""(\d+)p""").find(it.quality)?.groupValues?.get(1)?.toIntOrNull() ?: 0 },
            ),
        ).reversed()
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = "preferred_quality"
            title = "Preferred quality"
            entries = arrayOf("1080p", "720p", "480p", "360p")
            entryValues = arrayOf("1080", "720", "480", "360")
            setDefaultValue("1080")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)
    }
}
