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
import okhttp3.HttpUrl.Companion.toHttpUrl
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

    companion object {
        private const val VIDEO_SLUG = "v"
        private const val TAG_SLUG = "tag"
        private const val HOT_SLUG = "hot"

        private const val TAG_LIST_PREF = "TAG_LIST"

        const val PREFIX_TAG = "$TAG_SLUG:"
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        fetchTagsListOnce()

        return GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment(HOT_SLUG)
                addQueryParameter("page", page.toString())
            }.build(),
            headers,
        )
    }

    override fun popularAnimeSelector(): String = "div.col-md-3.col-xs-6.item a[data-id]"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            title = element.attr("title")

            val thumb = element.selectFirst("img.v-thumb")
            thumbnail_url = thumb?.attr("data-src") ?: thumb?.attr("src")
        }
    }

    override fun popularAnimeNextPageSelector(): String = "a.page-link[rel=next]"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        fetchTagsListOnce()
        
        return GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addQueryParameter("page", page.toString())
            }.build(),
            headers,
        )
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        fetchTagsListOnce()
        
        val url = baseUrl.toHttpUrl().newBuilder()

        return if (query.isNotBlank()) {
            // Handle direct tag search from deep link
            if (query.startsWith(PREFIX_TAG)) {
                val tagValue = query.removePrefix(PREFIX_TAG)
                GET(
                    url.apply {
                        addPathSegment(TAG_SLUG)
                        addPathSegment(tagValue)
                        addQueryParameter("page", page.toString())
                    }.build(),
                    headers,
                )
            } else {
                // Regular search by text query
                GET(
                    url.apply {
                        addQueryParameter("q", query)
                        addQueryParameter("page", page.toString())
                    }.build(),
                    headers,
                )
            }
        } else {
            // Apply filters
            val tagFilter = filters.filterIsInstance<PapalahFilters.TagFilter>().firstOrNull()
            val sortFilter = filters.filterIsInstance<PapalahFilters.SortFilter>().firstOrNull()

            when {
                // Filter by tag
                tagFilter != null && !tagFilter.isEmpty() -> {
                    GET(
                        url.apply {
                            addPathSegment(TAG_SLUG)
                            addPathSegment(tagFilter.toUriPart())
                            addQueryParameter("page", page.toString())
                        }.build(),
                        headers,
                    )
                }
                // Sort filter (hot/latest)
                sortFilter != null && !sortFilter.isEmpty() -> {
                    GET(
                        url.apply {
                            addPathSegment(sortFilter.toUriPart())
                            addQueryParameter("page", page.toString())
                        }.build(),
                        headers,
                    )
                }
                // Default: latest
                else -> {
                    GET(
                        url.apply {
                            addQueryParameter("page", page.toString())
                        }.build(),
                        headers,
                    )
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

            // Extract tags and save them
            val videoTags = document.select("div.v-keywords a")
                .map { it.text() }
                .filter { it.isNotBlank() }

            if (videoTags.isNotEmpty()) {
                // Save tags to preferences
                savedTags = savedTags.plus(videoTags.map { Tag(it, it) }.toSet())

                // Set genre
                genre = videoTags.joinToString()
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
                setUrlWithoutDomain(response.request.url.toString())
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
        return AnimeFilterList(
            AnimeFilter.Header("NOTE: Filter will be ignored if search query is not empty!"),
            AnimeFilter.Separator(),
            PapalahFilters.TagFilter(
                if (!this::tagsArray.isInitialized && savedTags.isEmpty()) {
                    arrayOf(Tag("Reset filter to load tags", ""))
                } else {
                    setOf(Tag("<Select Tag>", ""))
                        .plus(if (this::tagsArray.isInitialized) tagsArray.toSet() else emptySet())
                        .plus(savedTags.minus(PapalahFilters.getPopularTags().toSet()))
                        .toTypedArray()
                },
            ),
            PapalahFilters.SortFilter(),
        )
    }

    // ===================== Auto-Fetch Tags Mechanism ======================

    /**
     * Automatically fetched tags from the source to be used in the filters.
     */
    private lateinit var tagsArray: Tags

    /**
     * The request to the page that have the tags list.
     */
    private fun tagsListRequest() = GET(baseUrl, headers)

    /**
     * Fetch the tags from the source to be used in the filters.
     */
    private fun fetchTagsListOnce() {
        if (!this::tagsArray.isInitialized) {
            runCatching {
                client.newCall(tagsListRequest())
                    .execute()
                    .asJsoup()
                    .let(::tagsListParse)
                    .let { tags ->
                        if (tags.isNotEmpty()) {
                            tagsArray = tags
                        }
                    }
            }.onFailure { it.printStackTrace() }
        }
    }

    /**
     * Parse tags from the document (from footer keywords or any tag links).
     */
    private fun tagsListParse(document: Document): Tags {
        // Extract from footer keywords section
        val footerTags = document.select("div.keywords a[href*=/$TAG_SLUG/]")
            .mapNotNull { element ->
                val tagName = element.text().trim()
                val tagValue = element.attr("href")
                    .substringAfter("/$TAG_SLUG/")
                    .substringBefore("?")
                    .trim()

                if (tagName.isNotEmpty() && tagValue.isNotEmpty()) {
                    Tag(tagName, tagValue)
                } else {
                    null
                }
            }

        // Also try from video page keywords
        val videoTags = document.select("div.v-keywords a")
            .mapNotNull { element ->
                val tagName = element.text().trim()
                if (tagName.isNotEmpty()) Tag(tagName, tagName) else null
            }

        return (footerTags + videoTags)
            .distinctBy { it.second }
            .toTypedArray()
    }

    /**
     * Saved tags from previously viewed videos.
     */
    private var savedTags: Set<Tag> = loadTagListFromPreferences()
        set(value) {
            preferences.edit().putStringSet(
                TAG_LIST_PREF,
                value.map { it.first }.toSet(),
            ).apply()
            field = value
        }

    /**
     * Load saved tags from SharedPreferences.
     */
    private fun loadTagListFromPreferences(): Set<Tag> =
        preferences.getStringSet(TAG_LIST_PREF, emptySet())
            ?.mapNotNull { tagName -> 
                if (tagName.isNotBlank()) Tag(tagName, tagName) else null 
            }
            ?.toSet()
            ?: emptySet()

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

typealias Tags = Array<Tag>
typealias Tag = Pair<String, String>
