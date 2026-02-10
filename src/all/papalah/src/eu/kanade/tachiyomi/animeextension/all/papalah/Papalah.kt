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

    override val lang = "all"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        // Popular URL pattern: /hot?&sort=2&duration=3&page=2
        val url = "$baseUrl/hot?&sort=2&duration=3&page=$page"
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = 
        "div.row:not(:has(div.sponsor-outer)) div.col-md-3.col-xs-6.item"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            // Ambil HANYA <a> pertama untuk avoid duplikasi
            val link = element.selectFirst("a[data-id]") ?: return@apply

            val href = (link.attr("href") ?: "").removePrefix(".")
            setUrlWithoutDomain(if (href.startsWith("/")) href else "/$href")

            title = (link.attr("title") ?: "").ifEmpty {
                element.selectFirst("div.v-name")?.text() ?: ""
            }

            val thumb = element.selectFirst("img.v-thumb")
            thumbnail_url = thumb?.attr("data-src") ?: thumb?.attr("src")
        }
    }

    override fun popularAnimeNextPageSelector(): String = "a.page-link[rel=next]"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        // Latest URL pattern: /?&page=3
        val url = "$baseUrl/?&page=$page"
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            // Search by query: /?q=test&page=2
            val searchUrl = "$baseUrl/?q=$query&page=$page"
            GET(searchUrl, headers)
        } else {
            // Apply filters
            val tagFilter = filters.filterIsInstance<PapalahFilters.TagFilter>().firstOrNull()
            val sortFilter = filters.filterIsInstance<PapalahFilters.SortFilter>().firstOrNull()

            when {
                // Filter by tag: /tag/巨乳?page=2
                tagFilter != null && !tagFilter.isEmpty() -> {
                    val tagUrl = "$baseUrl/tag/${tagFilter.toUriPart()}?page=$page"
                    GET(tagUrl, headers)
                }
                // Sort filter (hot/latest)
                sortFilter != null && !sortFilter.isEmpty() -> {
                    if (sortFilter.toUriPart() == "hot") {
                        popularAnimeRequest(page)
                    } else {
                        latestUpdatesRequest(page)
                    }
                }
                // Default: latest
                else -> {
                    latestUpdatesRequest(page)
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

            // Extract video URL untuk tambahkan ke description (debug purpose)
            val videoUrl = document.selectFirst("video#my-video_html5_api")?.attr("src")
                ?: document.selectFirst("video source")?.attr("src")
                ?: ""

            description = buildString {
                document.selectFirst("div.v-name")?.text()?.let {
                    append("📹 $it\n\n")
                }

                document.selectFirst("span.timeago")?.attr("title")?.let {
                    append("📅 Upload: $it\n")
                }

                document.selectFirst("span.views-text")?.text()?.let {
                    append("👁️ Views: $it\n")
                }

                document.selectFirst("div.v-duration")?.text()?.let {
                    append("⏱️ Duration: $it\n")
                }

                // Tambahkan tags jika ada
                if (genre.isNotEmpty()) {
                    append("\n🏷️ Tags: $genre\n")
                }

                // Tambahkan video URL untuk debug
                if (videoUrl.isNotEmpty()) {
                    append("\n🎬 Source: $videoUrl")
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
        // Fetch tags dynamically dari /tag-list
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

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // No preferences needed for now
    }

    // ============================= Utilities ==============================

    private fun parseDate(dateStr: String?): Long {
        return try {
            dateStr?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).parse(it)?.time
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
