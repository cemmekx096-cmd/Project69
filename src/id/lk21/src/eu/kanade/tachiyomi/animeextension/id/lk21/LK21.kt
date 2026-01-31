package eu.kanade.tachiyomi.animeextension.id.lk21

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
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

class LK21 : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "LK21"

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
     * Fetch main domain from gateway
     */
    private fun getMainDomain(): String {
        return try {
            val cachedDomain = preferences.getString(PREF_CACHED_DOMAIN_KEY, null)
            val cacheTime = preferences.getLong(PREF_CACHE_TIME_KEY, 0)
            val currentTime = System.currentTimeMillis()

            // Cache valid for 6 hours
            if (cachedDomain != null && (currentTime - cacheTime) < 6 * 60 * 60 * 1000) {
                ReportLog.log("LK21-Domain", "Using cached domain: $cachedDomain", LogLevel.INFO)
                return cachedDomain
            }

            // Fetch new domain
            ReportLog.log("LK21-Domain", "Fetching new domain from gateway: $gatewayUrl", LogLevel.INFO)
            val response = client.newCall(GET(gatewayUrl, headers)).execute()
            val document = response.asJsoup()

            val mainDomain = document.selectFirst("a.cta-button.green-button")
                ?.attr("href")
                ?.trimEnd('/')
                ?: preferences.getString(PREF_BASE_URL_KEY, PREF_BASE_URL_DEFAULT)!!

            // Cache the domain
            preferences.edit()
                .putString(PREF_CACHED_DOMAIN_KEY, mainDomain)
                .putLong(PREF_CACHE_TIME_KEY, currentTime)
                .apply()

            ReportLog.log("LK21-Domain", "New domain fetched: $mainDomain", LogLevel.INFO)
            mainDomain
        } catch (e: Exception) {
            ReportLog.reportError("LK21-Domain", "Failed to fetch domain: ${e.message}")
            preferences.getString(PREF_BASE_URL_KEY, PREF_BASE_URL_DEFAULT)!!
        }
    }

    override val client: OkHttpClient
        get() {
            val timeoutSeconds = preferences.getString(PREF_TIMEOUT_KEY, PREF_TIMEOUT_DEFAULT)!!
                .toLongOrNull() ?: 90L

            return network.client.newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build()
        }

    override fun headersBuilder(): Headers.Builder {
        val userAgent = preferences.getString(PREF_USER_AGENT_KEY, PREF_USER_AGENT_DEFAULT)!!

        return super.headersBuilder().apply {
            add("User-Agent", userAgent)
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            add("Referer", baseUrl)
        }
    }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page"
        ReportLog.log("LK21-Popular", "Loading page $page: $url", LogLevel.INFO)
        return GET(url, headers)
    }

    override fun popularAnimeSelector(): String = "ul.sliders li.slider"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            val link = element.selectFirst("a")!!
            setUrlWithoutDomain(link.attr("href"))

            title = element.selectFirst("h3.poster-title")?.text() ?: ""
            thumbnail_url = element.selectFirst("img")?.attr("src") ?: ""

            ReportLog.log("LK21-Popular", "Parsed: $title", LogLevel.DEBUG)
        }
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a.next"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = LK21Filters.getSearchParameters(filters)

        val url = when {
            query.isNotEmpty() -> {
                val searchUrl = if (page == 1) {
                    "$baseUrl/?s=$query"
                } else {
                    "$baseUrl/page/$page/?s=$query"
                }
                ReportLog.log("LK21-Search", "Searching: $query (page $page)", LogLevel.INFO)
                searchUrl
            }
            params.genre.isNotEmpty() -> {
                val genreUrl = if (page == 1) {
                    "$baseUrl/genre/${params.genre}"
                } else {
                    "$baseUrl/genre/${params.genre}/page/$page"
                }
                ReportLog.log("LK21-Search", "Genre filter: ${params.genre}", LogLevel.INFO)
                genreUrl
            }
            params.country.isNotEmpty() -> {
                val countryUrl = if (page == 1) {
                    "$baseUrl/country/${params.country}"
                } else {
                    "$baseUrl/country/${params.country}/page/$page"
                }
                ReportLog.log("LK21-Search", "Country filter: ${params.country}", LogLevel.INFO)
                countryUrl
            }
            else -> popularAnimeRequest(page).url.toString()
        }

        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1[itemprop=name]")?.text() ?: ""
            thumbnail_url = document.selectFirst("picture img")?.attr("src") ?: ""

            genre = document.select("div.genre a")
                .joinToString(", ") { it.text() }

            // Check if series or movie
            val isSeriesElement = document.selectFirst("span.episode")
            status = if (isSeriesElement != null) {
                val episodeText = isSeriesElement.text()
                if (episodeText.contains("complete", ignoreCase = true)) {
                    SAnime.COMPLETED
                } else {
                    SAnime.ONGOING
                }
            } else {
                SAnime.COMPLETED // Movies are always completed
            }

            description = buildString {
                document.selectFirst("div.synopsis")?.text()?.let {
                    append("Synopsis:\n$it\n\n")
                }

                document.selectFirst("span.year")?.text()?.let {
                    append("Year: $it\n")
                }

                document.selectFirst("span.rating")?.text()?.let {
                    append("Rating: $it\n")
                }

                document.selectFirst("span.duration")?.text()?.let {
                    append("Duration: $it\n")
                }

                document.selectFirst("span.label")?.text()?.let {
                    append("Quality: $it\n")
                }
            }

            ReportLog.log("LK21-Detail", "Parsed anime: $title (Status: $status)", LogLevel.INFO)
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector(): String = "ul.episode-list li a, div.main-player"

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodeList = mutableListOf<SEpisode>()

        ReportLog.log("LK21-Episodes", "Parsing episodes from: ${response.request.url}", LogLevel.INFO)

        // Check if this is a series with episode list
        val episodeElements = document.select("ul.episode-list li a")

        if (episodeElements.isNotEmpty()) {
            // This is a series
            ReportLog.log("LK21-Episodes", "Found series with ${episodeElements.size} episodes", LogLevel.INFO)
            
            episodeElements.forEachIndexed { index, element ->
                episodeList.add(
                    SEpisode.create().apply {
                        val episodeUrl = element.attr("href")
                        setUrlWithoutDomain(episodeUrl)
                        
                        val episodeNumber = element.text().trim()
                        name = "Episode $episodeNumber"
                        episode_number = episodeNumber.toFloatOrNull() ?: (index + 1).toFloat()
                        date_upload = System.currentTimeMillis()
                    },
                )
            }
        } else {
            // This is a movie (single episode)
            ReportLog.log("LK21-Episodes", "This is a movie (single episode)", LogLevel.INFO)
            
            episodeList.add(
                SEpisode.create().apply {
                    setUrlWithoutDomain(response.request.url.toString())
                    name = "Movie"
                    episode_number = 1f
                    date_upload = System.currentTimeMillis()
                },
            )
        }

        return episodeList.reversed()
    }

    override fun episodeFromElement(element: Element): SEpisode = throw UnsupportedOperationException()

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        ReportLog.log("LK21-Video", "=== VIDEO PARSE START ===", LogLevel.INFO)
        ReportLog.log("LK21-Video", "Page URL: ${response.request.url}", LogLevel.INFO)

        // Parse player list
        val playerItems = document.select("ul#player-list li a")
        
        ReportLog.log("LK21-Video", "Found ${playerItems.size} players", LogLevel.INFO)

        playerItems.forEachIndexed { index, element ->
            try {
                val serverName = element.text().trim()
                val dataServer = element.attr("data-server")
                val dataUrl = element.attr("data-url")

                ReportLog.log("LK21-Video", "[$index] Server: $serverName (Type: $dataServer)", LogLevel.DEBUG)

                if (dataUrl.isNotEmpty()) {
                    // Build player URL
                    val playerUrl = when {
                        dataUrl.startsWith("http") -> dataUrl
                        else -> "$playerBase$dataServer/$dataUrl"
                    }

                    ReportLog.log("LK21-Video", "[$index] Player URL: $playerUrl", LogLevel.DEBUG)

                    // Extract video from player
                    extractVideoFromPlayer(playerUrl, videoList, serverName)
                }
            } catch (e: Exception) {
                ReportLog.reportError("LK21-Video", "[$index] Error: ${e.message}")
            }
        }

        // Also check for direct iframe in main-player
        document.selectFirst("iframe#main-player")?.let { iframe ->
            val iframeSrc = iframe.attr("src")
            if (iframeSrc.isNotEmpty()) {
                ReportLog.log("LK21-Video", "Found direct iframe: $iframeSrc", LogLevel.INFO)
                extractVideoFromPlayer(iframeSrc, videoList, "Direct Player")
            }
        }

        ReportLog.log("LK21-Video", "=== TOTAL VIDEOS: ${videoList.size} ===", LogLevel.INFO)

        return videoList.ifEmpty {
            ReportLog.log("LK21-Video", "No videos found, returning WebView option", LogLevel.WARN)
            listOf(Video(response.request.url.toString(), "Open in WebView", response.request.url.toString()))
        }
    }

    /**
     * Extract video from player iframe
     */
    private fun extractVideoFromPlayer(playerUrl: String, videoList: MutableList<Video>, serverName: String) {
        try {
            ReportLog.log("LK21-Extractor", "Extracting from: $playerUrl", LogLevel.INFO)

            // Simple extractor - load iframe and look for video sources
            val response = client.newCall(GET(playerUrl, headers)).execute()
            val html = response.body.string()

            // Method 1: Look for .m3u8 URLs
            val m3u8Regex = Regex("""["'](https?://[^"']*\.m3u8[^"']*)["']""")
            m3u8Regex.findAll(html).forEach { match ->
                val m3u8Url = match.groupValues[1]
                ReportLog.log("LK21-Extractor", "Found M3U8: $m3u8Url", LogLevel.DEBUG)
                
                videoList.add(
                    Video(
                        url = m3u8Url,
                        quality = "$serverName - HLS",
                        videoUrl = m3u8Url,
                        headers = Headers.headersOf("Referer", playerUrl),
                    )
                )
            }

            // Method 2: Look for .mp4 URLs
            val mp4Regex = Regex("""["'](https?://[^"']*\.mp4[^"']*)["']""")
            mp4Regex.findAll(html).forEach { match ->
                val mp4Url = match.groupValues[1]
                ReportLog.log("LK21-Extractor", "Found MP4: $mp4Url", LogLevel.DEBUG)
                
                // Try to determine quality from URL
                val quality = when {
                    mp4Url.contains("1080", ignoreCase = true) -> "$serverName - 1080p"
                    mp4Url.contains("720", ignoreCase = true) -> "$serverName - 720p"
                    mp4Url.contains("480", ignoreCase = true) -> "$serverName - 480p"
                    mp4Url.contains("360", ignoreCase = true) -> "$serverName - 360p"
                    else -> "$serverName - MP4"
                }
                
                videoList.add(
                    Video(
                        url = mp4Url,
                        quality = quality,
                        videoUrl = mp4Url,
                        headers = Headers.headersOf("Referer", playerUrl),
                    )
                )
            }

            // Method 3: Look for video sources in source tags
            val sourceRegex = Regex("""<source[^>]+src=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
            sourceRegex.findAll(html).forEach { match ->
                val sourceUrl = match.groupValues[1]
                ReportLog.log("LK21-Extractor", "Found source tag: $sourceUrl", LogLevel.DEBUG)
                
                if (sourceUrl.contains("http")) {
                    videoList.add(
                        Video(
                            url = sourceUrl,
                            quality = "$serverName - Source",
                            videoUrl = sourceUrl,
                            headers = Headers.headersOf("Referer", playerUrl),
                        )
                    )
                }
            }

            // Method 4: Look for common player configs
            val configRegex = Regex("""["']?file["']?\s*:\s*["']([^"']+)["']""")
            configRegex.findAll(html).forEach { match ->
                val fileUrl = match.groupValues[1]
                if (fileUrl.startsWith("http")) {
                    ReportLog.log("LK21-Extractor", "Found config file: $fileUrl", LogLevel.DEBUG)
                    
                    videoList.add(
                        Video(
                            url = fileUrl,
                            quality = "$serverName - Config",
                            videoUrl = fileUrl,
                            headers = Headers.headersOf("Referer", playerUrl),
                        )
                    )
                }
            }

            // If no videos found, add iframe as fallback
            if (videoList.isEmpty()) {
                ReportLog.log("LK21-Extractor", "No direct video found, adding iframe fallback", LogLevel.WARN)
                videoList.add(
                    Video(
                        url = playerUrl,
                        quality = "$serverName (Iframe)",
                        videoUrl = playerUrl,
                    )
                )
            }

        } catch (e: Exception) {
            ReportLog.reportError("LK21-Extractor", "Extraction failed for $playerUrl: ${e.message}"),
            e.printStackTrace(),
            // Add iframe as error fallback
            videoList.add(
                Video(
                    url = playerUrl,
                    quality = "$serverName (Error)",
                    videoUrl = playerUrl,
                )
            )
        }
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList = LK21Filters.getFilterList()

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = "Fallback Base URL"
            summary = "Default: $PREF_BASE_URL_DEFAULT\nCurrent: %s\n\nDigunakan jika gateway gagal"
            setDefaultValue(PREF_BASE_URL_DEFAULT)
            dialogTitle = "Enter Fallback URL"

            setOnPreferenceChangeListener { _, newValue ->
                val newUrl = newValue as String
                if (newUrl.isNotBlank() && newUrl.startsWith("http")) {
                    preferences.edit().putString(key, newUrl.trimEnd('/')).commit()
                } else {
                    false
                }
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_USER_AGENT_KEY
            title = "User Agent"
            summary = "Custom User Agent\n\nDefault: Chrome Windows\nCurrent: %s"
            setDefaultValue(PREF_USER_AGENT_DEFAULT)
            dialogTitle = "Enter User Agent"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_TIMEOUT_KEY
            title = "Network Timeout (seconds)"
            summary = "Timeout untuk network requests\n\nDefault: 90\nCurrent: %s"
            setDefaultValue(PREF_TIMEOUT_DEFAULT)
            dialogTitle = "Enter Timeout"

            setOnPreferenceChangeListener { _, newValue ->
                val timeout = (newValue as String).toLongOrNull()
                if (timeout != null && timeout > 0) {
                    preferences.edit().putString(key, newValue).commit()
                } else {
                    false
                }
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred Quality"
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "Pilih kualitas video default: %s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)
    }

    // ============================= Companion ==============================

    companion object {
        private const val PREF_BASE_URL_KEY = "base_url"
        private const val PREF_BASE_URL_DEFAULT = "https://tv7.lk21official.cc"
        
        private const val PREF_CACHED_DOMAIN_KEY = "cached_domain"
        private const val PREF_CACHE_TIME_KEY = "cache_time"

        private const val PREF_USER_AGENT_KEY = "user_agent"
        private const val PREF_USER_AGENT_DEFAULT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private const val PREF_TIMEOUT_KEY = "network_timeout"
        private const val PREF_TIMEOUT_DEFAULT = "90"

        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
        private val PREF_QUALITY_VALUES = arrayOf("1080p", "720p", "480p", "360p")
    }
}
