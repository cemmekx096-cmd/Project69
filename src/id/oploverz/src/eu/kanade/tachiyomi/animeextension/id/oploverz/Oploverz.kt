package eu.kanade.tachiyomi.animeextension.id.oploverz

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.parallelCatchingFlatMapBlocking
import extensions.utils.getPreferencesLazy
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Oploverz : ConfigurableAnimeSource, AnimeHttpSource() {

    override val name = "Oploverz"
    override val baseUrl = "https://vip.oploverz.ltd"
    private val apiUrl = "https://backapi.oploverz.ac/api"
    override val lang = "id"
    override val supportsLatest = true

    private val preferences by getPreferencesLazy()
    private val tracker = FeatureTracker("Oploverz")

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val extractor by lazy { OploverzExtractor(client, headers) }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        tracker.debug("popularAnimeRequest: page=$page")
        return GET("$apiUrl/series?page=$page&orderBy=popular", headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val data = json.decodeFromString<SeriesListResponse>(response.body.string())
        tracker.debug("popularAnimeParse: got ${data.data.size} items, lastPage=${data.meta.lastPage}")
        return AnimesPage(
            data.data.map { it.toSAnime() },
            data.meta.currentPage < data.meta.lastPage,
        )
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        tracker.debug("latestUpdatesRequest: page=$page")
        return GET("$apiUrl/episodes?page=$page&orderBy=latest", headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val data = json.decodeFromString<EpisodeListResponse>(response.body.string())
        tracker.debug("latestUpdatesParse: got ${data.data.size} items")

        // Deduplicate series dari episode list
        val seen = mutableSetOf<Int>()
        val animes = data.data.mapNotNull { ep ->
            ep.series?.takeIf { seen.add(it.id) }?.toSAnime()
        }
        return AnimesPage(animes, data.meta.currentPage < data.meta.lastPage)
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) addQueryParameter("title", query)

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> if (filter.state != 0) {
                        addQueryParameter("genre", filter.toValue())
                    }
                    is StatusFilter -> if (filter.state != 0) {
                        addQueryParameter("status", filter.toValue())
                    }
                    is TypeFilter -> if (filter.state != 0) {
                        addQueryParameter("type", filter.toValue())
                    }
                    is OrderFilter -> if (filter.state != 0) {
                        addQueryParameter("orderBy", filter.toValue())
                    }
                    else -> {}
                }
            }
        }.build()

        tracker.debug("searchAnimeRequest: $url")
        return GET(url.toString(), headers)
    }

    override fun searchAnimeParse(response: Response) = popularAnimeParse(response)

    // ============================= Filters ================================

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Filter diabaikan jika ada query teks"),
        GenreFilter(),
        StatusFilter(),
        TypeFilter(),
        OrderFilter(),
    )

    private class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("Semua", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Magic", "magic"),
            Pair("Mecha", "mecha"),
            Pair("Military", "military"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("School", "school"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Thriller", "thriller"),
            Pair("Vampire", "vampire"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "Ongoing"),
            Pair("Completed", "Completed"),
        ),
    )

    private class TypeFilter : UriPartFilter(
        "Tipe",
        arrayOf(
            Pair("Semua", ""),
            Pair("TV", "TV"),
            Pair("Movie", "Movie"),
            Pair("OVA", "OVA"),
            Pair("ONA", "ONA"),
            Pair("Special", "Special"),
        ),
    )

    private class OrderFilter : UriPartFilter(
        "Urutkan",
        arrayOf(
            Pair("Default", ""),
            Pair("Popular", "popular"),
            Pair("Terbaru", "latest"),
            Pair("Rating", "rating"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toValue() = vals[state].second
    }

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        val slug = anime.url.removePrefix("/series/").removePrefix("/movie/").trimEnd('/')
        tracker.debug("animeDetailsRequest: slug=$slug")
        return GET("$apiUrl/series/$slug", headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val data = json.decodeFromString<SeriesDetailResponse>(response.body.string()).data
        tracker.debug("animeDetailsParse: title=${data.title}")
        return SAnime.create().apply {
            url = "/series/${data.slug}"
            title = data.title
            thumbnail_url = data.poster
            description = buildString {
                data.japaneseTitle?.also { append("Japanese: $it\n") }
                data.duration?.also { append("Durasi: $it\n") }
                data.score?.also { append("Score: $it\n") }
                data.releaseDate?.take(10)?.also { append("Rilis: $it\n") }
                data.season?.also { append("Season: ${it.name}\n") }
                data.studio?.also { append("Studio: ${it.name}\n") }
                if (isNotEmpty()) append("\n")
                data.description?.also { append(it) }
            }.trim()
            genre = data.genres?.joinToString(", ") { it.name }
            status = when (data.status?.lowercase()) {
                "ongoing" -> SAnime.ONGOING
                "completed" -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
            author = data.studio?.name
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request {
        val slug = anime.url.removePrefix("/series/").removePrefix("/movie/").trimEnd('/')
        tracker.debug("episodeListRequest: slug=$slug")
        // Ambil series detail dulu untuk dapat seriesId
        return GET("$apiUrl/series/$slug", headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val series = json.decodeFromString<SeriesDetailResponse>(response.body.string()).data
        tracker.debug("episodeListParse: slug=${series.slug} title=${series.title}")

        // Fetch page 1 dulu untuk tahu lastPage
        val firstResp = client.newCall(
            GET("$apiUrl/series/${series.slug}/episodes?page=1", headers),
        ).execute()
        val firstData = json.decodeFromString<EpisodeListResponse>(firstResp.body.string())
        val lastPage = firstData.meta.lastPage

        // Fetch semua page secara parallel
        val episodes = (1..lastPage).toList().parallelCatchingFlatMapBlocking { page ->
            val epResp = client.newCall(
                GET("$apiUrl/series/${series.slug}/episodes?page=$page", headers),
            ).execute()
            json.decodeFromString<EpisodeListResponse>(epResp.body.string()).data
        }

        tracker.debug("episodeListParse: total ${episodes.size} episodes")

        return episodes.map { ep ->
            SEpisode.create().apply {
                url = "/episode/${ep.id}"
                name = "Episode ${ep.episodeNumber}" +
                    (ep.title?.let { " - $it" } ?: "")
                episode_number = ep.episodeNumber.toFloatOrNull() ?: 0F
                date_upload = ep.releasedAt?.let { parseDate(it) } ?: 0L
            }
        }.sortedBy { it.episode_number }
    }

    // ============================ Video Links =============================

    override fun videoListRequest(episode: SEpisode): Request {
        val id = episode.url.removePrefix("/episode/")
        tracker.debug("videoListRequest: episodeId=$id")
        return GET("$apiUrl/episodes/$id", headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val data = json.decodeFromString<EpisodeDetailResponse>(response.body.string()).data
        tracker.debug("videoListParse: episodeId=${data.id} streamUrls=${data.streamUrl?.size}")

        val streamUrls = data.streamUrl ?: run {
            tracker.error("videoListParse: streamUrl is null")
            return emptyList()
        }

        return streamUrls.parallelCatchingFlatMapBlocking { stream ->
            extractor.videosFromStreamUrl(listOf(stream))
        }
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

    private fun SeriesItem.toSAnime(): SAnime {
        return SAnime.create().apply {
            url = "/series/$slug"
            title = this@toSAnime.title
            thumbnail_url = poster
            genre = genres?.joinToString(", ") { it.name }
            status = when (this@toSAnime.status?.lowercase()) {
                "ongoing" -> SAnime.ONGOING
                "completed" -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
        }
    }

    private fun parseDate(dateStr: String): Long {
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .parse(dateStr)?.time ?: 0L
        }.getOrDefault(0L)
    }

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_TITLE = "Kualitas pilihan"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
    }
}
