package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.id.lk21movies.extractors.LK21MoviesExtractorFactory
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class LK21Movies : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "LK21 Movies"

    override val lang = "id"

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    // ============================= Gateway & Domain =============================

    private val gatewayUrl = "https://d21.team/"
    private val downloadBase = "https://dl.lk21.party/"
    private val playerBase = "https://playeriframe.sbs/iframe/"
    private val posterBase = "https://poster.lk21.party/"

    override val baseUrl: String
        get() = getMainDomain()

    /**
     * Fetch main domain from gateway, dengan cache 6 jam.
     * Fallback ke PREF_BASE_URL_KEY jika gagal.
     */
    /**
     * Headers khusus untuk fetch gateway — dibuat manual tanpa menyentuh
     * [headers] / [headersBuilder] agar tidak terjadi circular dependency.
     * (headersBuilder → baseUrl → getMainDomain → headers → headersBuilder → ♾️)
     */
    private val gatewayHeaders: Headers by lazy {
        val userAgent = preferences.getString(PREF_USER_AGENT_KEY, PREF_USER_AGENT_DEFAULT)!!
        Headers.Builder()
            .add("User-Agent", userAgent)
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()
    }

    private fun getMainDomain(): String {
        return try {
            val cachedDomain = preferences.getString(PREF_CACHED_DOMAIN_KEY, null)
            val cacheTime = preferences.getLong(PREF_CACHE_TIME_KEY, 0)
            val currentTime = System.currentTimeMillis()

            // Cache valid selama 6 jam
            if (cachedDomain != null && (currentTime - cacheTime) < 6 * 60 * 60 * 1000) {
                return cachedDomain
            }

            // Fetch domain baru — pakai gatewayHeaders, BUKAN headers (mencegah circular)
            val response = network.client.newCall(GET(gatewayUrl, gatewayHeaders)).execute()
            val document = response.asJsoup()

            val mainDomain = document.selectFirst("a.cta-button.green-button")
                ?.attr("href")
                ?.trimEnd('/')
                ?: preferences.getString(PREF_BASE_URL_KEY, PREF_BASE_URL_DEFAULT)!!

            // Simpan ke cache
            preferences.edit()
                .putString(PREF_CACHED_DOMAIN_KEY, mainDomain)
                .putLong(PREF_CACHE_TIME_KEY, currentTime)
                .apply()

            mainDomain
        } catch (e: Exception) {
            // Fallback: gunakan URL custom user atau default
            preferences.getString(PREF_BASE_URL_KEY, PREF_BASE_URL_DEFAULT)!!
        }
    }

    override val client: OkHttpClient
        get() {
            val timeoutSeconds = preferences.getString(PREF_TIMEOUT_KEY, PREF_TIMEOUT_DEFAULT)!!
                .toLongOrNull() ?: 90L

            return network.cloudflareClient.newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()
        }

    override fun headersBuilder(): Headers.Builder {
        val userAgent = preferences.getString(PREF_USER_AGENT_KEY, PREF_USER_AGENT_DEFAULT)!!

        // Referer TIDAK diset di sini karena memanggil baseUrl akan trigger
        // getMainDomain() → butuh headers → headersBuilder() → circular StackOverflow.
        return super.headersBuilder().apply {
            add("User-Agent", userAgent)
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        }
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/populer/"
        } else {
            "$baseUrl/populer/page/$page/"
        }
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "li.slider"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            // Cek apakah ini series/drama - jika ya, skip
            if (element.hasClass("episode") || element.hasClass("episode.complete")) {
                return@apply
            }

            val link = element.selectFirst("article figure a")

            setUrlWithoutDomain(link?.attr("href") ?: "")
            title = link?.attr("title") ?: ""

            // Ambil poster dari picture > img
            thumbnail_url = element.selectFirst("picture img")?.attr("src")
                ?: element.selectFirst("picture source")?.attr("srcset")
        }
    }

    override fun popularAnimeNextPageSelector(): String = "a.next"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) {
            "$baseUrl/latest/"
        } else {
            "$baseUrl/latest/page/$page/"
        }
        return GET(url, headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime {
        return popularAnimeFromElement(element)
    }

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            // Search by query
            val searchUrl = "$baseUrl/search?s=$query"
            GET(searchUrl, headers)
        } else {
            // Apply filters
            val typeFilter = filters.filterIsInstance<LK21MoviesFilters.TypeFilter>().firstOrNull()
            val genreFilter = filters.filterIsInstance<LK21MoviesFilters.GenreFilter>().firstOrNull()
            val genre2Filter = filters.filterIsInstance<LK21MoviesFilters.Genre2Filter>().firstOrNull()
            val countryFilter = filters.filterIsInstance<LK21MoviesFilters.CountryFilter>().firstOrNull()
            val yearFilter = filters.filterIsInstance<LK21MoviesFilters.YearFilter>().firstOrNull()
            val sortFilter = filters.filterIsInstance<LK21MoviesFilters.SortFilter>().firstOrNull()

            // Build filter URL
            val filterUrl = buildString {
                append(baseUrl)

                // Sort (Popular atau Latest)
                when (sortFilter?.toUriPart()) {
                    "populer" -> append("/populer/")
                    else -> append("/latest/")
                }

                // Tambahkan query params untuk filter
                val params = mutableListOf<String>()

                typeFilter?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                    params.add("type=$it")
                }

                genreFilter?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                    params.add("genre1=$it")
                }

                genre2Filter?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                    params.add("genre2=$it")
                }

                countryFilter?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                    params.add("country=$it")
                }

                yearFilter?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
                    params.add("tahun=$it")
                }

                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }

                // Pagination
                if (page > 1) {
                    append(if (params.isEmpty()) "?" else "&")
                    append("page=$page")
                }
            }

            GET(filterUrl, headers)
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime {
        return popularAnimeFromElement(element)
    }

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            // Title dari meta atau h1
            title = document.selectFirst("div.detail h1")?.text()
                ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?: ""

            // Poster/Thumbnail
            thumbnail_url = document.selectFirst("picture img")?.attr("src")
                ?: document.selectFirst("picture source")?.attr("srcset")
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")

            // Genre
            genre = document.select("div.tag-list a[href*=/genre/], a[href*=/country/]")
                .joinToString(", ") { it.text() }

            // Author = Director
            author = document.select("div.detail p a[href*=/director/]")
                .joinToString(", ") { it.text() }

            // Artist = Cast
            artist = document.select("div.detail p a[href*=/artist/]")
                .take(5) // Ambil 5 aktor utama saja
                .joinToString(", ") { it.text() }

            // Description
            description = buildString {
                // Sinopsis
                document.selectFirst("div.meta-info div.synopsis")?.text()?.let {
                    append(it.trim())
                    append("\n\n")
                }

                // Rating
                document.selectFirst("span.rating")?.text()?.let {
                    append("⭐ Rating: $it\n")
                }

                // Tahun
                document.select("div.detail p").forEach { p ->
                    val text = p.text()
                    if (text.contains("Tahun:", ignoreCase = true)) {
                        append("📅 $text\n")
                    }
                }

                // Durasi
                document.select("div.detail p").forEach { p ->
                    val text = p.text()
                    if (text.contains("Durasi:", ignoreCase = true)) {
                        append("⏱️ $text\n")
                    }
                }

                // Director
                if (!author.isNullOrEmpty()) {
                    append("🎬 Sutradara: $author\n")
                }

                // Cast
                if (!artist.isNullOrEmpty()) {
                    append("🎭 Pemeran: $artist\n")
                }

                // Genre
                if (!genre.isNullOrEmpty()) {
                    append("🏷️ Genre: $genre\n")
                }

                // Trailer link
                document.selectFirst("a.yt-lightbox")?.attr("href")?.let { trailerUrl ->
                    append("\n🎥 Trailer: $trailerUrl\n")
                }

                // Poster URL (for debugging)
                thumbnail_url?.let {
                    append("\n🖼️ Poster: $it")
                }
            }

            status = SAnime.COMPLETED
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()

        // LK21 adalah site untuk MOVIE saja, bukan series
        // Jadi hanya ada 1 episode per film
        return listOf(
            SEpisode.create().apply {
                name = document.selectFirst("div.detail h1")?.text() ?: "Movie"
                episode_number = 1F

                // Ambil URL dari response
                val url = response.request.url.toString()
                setUrlWithoutDomain(url.removePrefix(baseUrl))

                // Tanggal upload (jika ada)
                date_upload = 0L
            },
        )
    }

    override fun episodeListSelector(): String = throw Exception("Not used")

    override fun episodeFromElement(element: Element): SEpisode = throw Exception("Not used")

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val factory = LK21MoviesExtractorFactory(client, headers)

        // Extract semua server dari player-list
        val servers = document.select("ul#player-list li a")

        val videos = mutableListOf<Video>()

        servers.forEach { serverElement ->
            val serverName = serverElement.attr("data-server") ?: "Unknown"
            val serverUrl = serverElement.attr("data-url") ?: ""

            if (serverUrl.isNotEmpty()) {
                try {
                    // Extract videos dari setiap server
                    val extractedVideos = factory.extractFromServer(serverUrl, serverName, response.request.url.toString())
                    videos.addAll(extractedVideos)
                } catch (e: Exception) {
                    android.util.Log.e("LK21", "Error extracting from $serverName: ${e.message}")
                }
            }
        }

        // Jika tidak ada server list, coba extract dari main player
        if (videos.isEmpty()) {
            val mainIframe = document.selectFirst("iframe#main-player")?.attr("src")
            if (!mainIframe.isNullOrEmpty()) {
                val extractedVideos = factory.extractFromServer(mainIframe, "Main Player", response.request.url.toString())
                videos.addAll(extractedVideos)
            }
        }

        return videos.distinctBy { it.url }
    }

    override fun videoListSelector(): String = throw Exception("Not used")

    override fun videoFromElement(element: Element): Video = throw Exception("Not used")

    override fun videoUrlParse(document: Document): String = throw Exception("Not used")

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("NOTE: Filter diabaikan jika search query tidak kosong!"),
            AnimeFilter.Separator(),
            LK21MoviesFilters.SortFilter(),
            LK21MoviesFilters.TypeFilter(),
            LK21MoviesFilters.GenreFilter(),
            LK21MoviesFilters.Genre2Filter(),
            LK21MoviesFilters.CountryFilter(),
            LK21MoviesFilters.YearFilter(),
        )
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // --- Fallback Base URL ---
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = "Fallback Base URL"
            summary = "Digunakan jika gateway gagal mengambil domain.\nDefault: $PREF_BASE_URL_DEFAULT\nCurrent: %s"
            setDefaultValue(PREF_BASE_URL_DEFAULT)
            dialogTitle = "Masukkan Base URL"
            dialogMessage = "Contoh: https://tv8.lk21official.cc"
            setOnPreferenceChangeListener { _, newValue ->
                // Reset domain cache agar domain baru langsung dipakai
                preferences.edit()
                    .remove(PREF_CACHED_DOMAIN_KEY)
                    .remove(PREF_CACHE_TIME_KEY)
                    .apply()
                true
            }
        }.also(screen::addPreference)

        // --- User Agent ---
        EditTextPreference(screen.context).apply {
            key = PREF_USER_AGENT_KEY
            title = "User Agent"
            summary = "Custom User Agent untuk request.\nDefault: Chrome Windows\nCurrent: %s"
            setDefaultValue(PREF_USER_AGENT_DEFAULT)
            dialogTitle = "Masukkan User Agent"
            dialogMessage = "Kosongkan untuk menggunakan default."
            setOnPreferenceChangeListener { _, _ -> true }
        }.also(screen::addPreference)

        // --- Network Timeout ---
        EditTextPreference(screen.context).apply {
            key = PREF_TIMEOUT_KEY
            title = "Network Timeout (detik)"
            summary = "Batas waktu untuk setiap network request.\nDefault: $PREF_TIMEOUT_DEFAULT detik\nCurrent: %s"
            setDefaultValue(PREF_TIMEOUT_DEFAULT)
            dialogTitle = "Masukkan Timeout (detik)"
            dialogMessage = "Angka bulat, contoh: 60 atau 120."
            setOnPreferenceChangeListener { _, newValue ->
                newValue.toString().toLongOrNull() != null
            }
        }.also(screen::addPreference)

        // --- Preferred Quality ---
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred Quality"
            summary = "Pilih kualitas video yang diutamakan.\nCurrent: %s"
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            setOnPreferenceChangeListener { _, _ -> true }
        }.also(screen::addPreference)
    }

    companion object {
        const val PREFIX_SEARCH = "id:"

        // --- Preference Keys ---
        const val PREF_BASE_URL_KEY = "pref_base_url"
        const val PREF_BASE_URL_DEFAULT = "https://tv8.lk21official.cc"

        const val PREF_USER_AGENT_KEY = "pref_user_agent"
        const val PREF_USER_AGENT_DEFAULT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        const val PREF_TIMEOUT_KEY = "pref_timeout"
        const val PREF_TIMEOUT_DEFAULT = "90"

        const val PREF_QUALITY_KEY = "pref_quality"
        const val PREF_QUALITY_DEFAULT = "720p"
        val PREF_QUALITY_ENTRIES = arrayOf("Auto", "1080p", "720p", "480p", "360p")
        val PREF_QUALITY_VALUES = arrayOf("auto", "1080", "720", "480", "360")

        // --- Cache Keys (internal, tidak ditampilkan di UI) ---
        const val PREF_CACHED_DOMAIN_KEY = "pref_cached_domain"
        const val PREF_CACHE_TIME_KEY = "pref_cache_time"
    }
}
