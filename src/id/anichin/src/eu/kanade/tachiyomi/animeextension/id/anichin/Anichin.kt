package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.anichinextractor.AnichinExtractor
import eu.kanade.tachiyomi.lib.cloudflareinterceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.googledriveextractor.GoogleDriveExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.lib.rubyvidhubextractor.RubyVidHubExtractor
import eu.kanade.tachiyomi.lib.rumbleextractor.RumbleExtractor
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

class Anichin : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Anichin"

    // ✨ Use preference for base URL
    override val baseUrl: String
        get() = preferences.getString(PREF_BASE_URL_KEY, PREF_BASE_URL_DEFAULT)!!

    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val cloudflareInterceptor by lazy {
        CloudflareInterceptor(network.client)
    }

    // ✨ Dynamic client with preferences
    override val client: OkHttpClient
        get() {
            val timeoutSeconds = preferences.getString(PREF_TIMEOUT_KEY, PREF_TIMEOUT_DEFAULT)!!
                .toLongOrNull() ?: 90L

            val builder = network.client.newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)

            // Add CloudFlare interceptor if enabled
            if (preferences.getBoolean(PREF_CLOUDFLARE_KEY, PREF_CLOUDFLARE_DEFAULT)) {
                builder.addInterceptor(cloudflareInterceptor)
            }

            return builder.build()
        }

    // ✨ Dynamic headers with custom user agent
    override fun headersBuilder(): Headers.Builder {
        val userAgent = preferences.getString(PREF_USER_AGENT_KEY, PREF_USER_AGENT_DEFAULT)!!

        return super.headersBuilder().apply {
            add("User-Agent", userAgent)
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            add("Referer", baseUrl)
            add("DNT", "1")
            add("Connection", "keep-alive")
            add("Upgrade-Insecure-Requests", "1")
        }
    }

    // Existing extractors
    private val okruExtractor by lazy { OkruExtractor(client) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client) }
    private val googleDriveExtractor by lazy { GoogleDriveExtractor(client, headers) }
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    // ✨ NEW EXTRACTORS
    private val anichinVipExtractor by lazy { AnichinExtractor(client) }
    private val rubyVidExtractor by lazy { RubyVidHubExtractor(client) }
    private val rumbleExtractor by lazy { RumbleExtractor(client) }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/ongoing/?page=$page", headers)
    }

    override fun popularAnimeSelector(): String = "div.listupd article.bs"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            setUrlWithoutDomain(element.selectFirst("div.bsx > a")!!.attr("href"))
            thumbnail_url = element.selectFirst("div.bsx img")?.attr("src")
            title = element.selectFirst("div.bsx a")?.attr("title") ?: ""
        }
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a.next"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/ongoing/?page=$page", headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = AnichinFilters.getSearchParameters(filters)

        val url = when {
            query.isNotEmpty() -> "$baseUrl/?s=$query&page=$page"
            params.genre.isNotEmpty() -> "$baseUrl/genres/${params.genre}/?page=$page"
            else -> "$baseUrl/ongoing/?page=$page"
        }

        return GET(url, headers)
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1.entry-title")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.thumb img")?.attr("src")

            genre = document.select("div.genxed a").joinToString { it.text() }

            status = when {
                document.select("div.status").text().contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
                document.select("div.status").text().contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }

            description = buildString {
                document.selectFirst("div.desc")?.text()?.let { append(it) }
            }
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector(): String = "div.eplister ul li"

    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            val episodeUrl = element.selectFirst("a")!!.attr("href")
            setUrlWithoutDomain(episodeUrl)

            val rawName = element.selectFirst("span.epcur")?.text()
                ?: element.selectFirst("a")?.text()
                ?: "Episode"

            name = if (rawName.length > 80) {
                rawName.take(77) + "..."
            } else {
                rawName
            }

            episode_number = rawName.filter { it.isDigit() }.toFloatOrNull() ?: 0f
            date_upload = System.currentTimeMillis()
        }
    }

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        android.util.Log.d("Anichin", "=== VIDEO PARSE START ===")
        android.util.Log.d("Anichin", "Page URL: ${response.request.url}")

        // Parse dropdown servers
        document.select("select.mirror option").forEachIndexed { index, option ->
            val serverValue = option.attr("value")
            val serverName = option.text().trim()

            android.util.Log.d("Anichin", "[$index] Server: $serverName")

            if (serverValue.isNotEmpty() && !serverName.contains("Select", ignoreCase = true)) {
                try {
                    // Skip [ADS] servers if preference enabled
                    if (preferences.getBoolean(PREF_SKIP_ADS_KEY, PREF_SKIP_ADS_DEFAULT) &&
                        serverName.contains("ADS", ignoreCase = true)
                    ) {
                        android.util.Log.d("Anichin", "[$index] Skipping ADS server")
                        return@forEachIndexed
                    }

                    // Decode base64
                    val decodedHtml = try {
                        String(android.util.Base64.decode(serverValue, android.util.Base64.DEFAULT))
                    } catch (e: Exception) {
                        android.util.Log.e("Anichin", "Base64 decode failed: ${e.message}")
                        serverValue
                    }

                    android.util.Log.d("Anichin", "[$index] Decoded HTML length: ${decodedHtml.length}")

                    // Extract iframe src
                    val iframeSrc = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(decodedHtml)
                        ?.groupValues
                        ?.get(1)

                    if (iframeSrc != null) {
                        val cleanUrl = when {
                            iframeSrc.startsWith("//") -> "https:$iframeSrc"
                            iframeSrc.startsWith("http") -> iframeSrc
                            else -> iframeSrc
                        }

                        android.util.Log.d("Anichin", "[$index] Iframe URL: $cleanUrl")
                        extractVideoFromUrl(cleanUrl, videoList, serverName)
                    } else {
                        android.util.Log.w("Anichin", "[$index] No iframe found")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Anichin", "[$index] Exception: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        android.util.Log.d("Anichin", "=== TOTAL VIDEOS: ${videoList.size} ===")

        return videoList.ifEmpty {
            listOf(Video(response.request.url.toString(), "Open in WebView", response.request.url.toString()))
        }
    }

    /**
     * Check if URL is a shortener service
     */
    private fun isUrlShortener(url: String): Boolean {
        val shorteners = listOf(
            "short.icu",
            "short.ink",
            "bit.ly",
            "t.ly",
            "tinyurl",
            "goo.gl",
            "ow.ly",
            "is.gd",
        )
        return shorteners.any { url.contains(it, ignoreCase = true) }
    }

    /**
     * Follow URL redirects for shorteners
     */
    private fun followRedirect(url: String): String {
        return try {
            android.util.Log.d("Anichin", "Following redirect: $url")
            val response = client.newCall(GET(url, headers)).execute()
            val finalUrl = response.request.url.toString()
            
            if (finalUrl != url) {
                android.util.Log.d("Anichin", "Redirected: $url → $finalUrl")
            }
            
            finalUrl
        } catch (e: Exception) {
            android.util.Log.e("Anichin", "Redirect failed: ${e.message}")
            url
        }
    }

    private fun extractVideoFromUrl(url: String, videoList: MutableList<Video>, serverName: String) {
        try {
            // ✨ Follow redirects for URL shorteners FIRST
            val finalUrl = if (isUrlShortener(url)) followRedirect(url) else url
            
            when {
                // ✨ Anichin VIP Stream (anichin.stream, anichinv2.icu)
                finalUrl.contains("anichin", ignoreCase = true) -> {
                    android.util.Log.d("Anichin", "Extracting Anichin VIP Stream")
                    val videos = anichinVipExtractor.videosFromUrl(finalUrl, serverName)
                    android.util.Log.d("Anichin", "Anichin VIP videos: ${videos.size}")
                    videoList.addAll(videos)
                }

                // ✨ RubyVidHub
                finalUrl.contains("rubyvidhub", ignoreCase = true) -> {
                    android.util.Log.d("Anichin", "Extracting RubyVidHub")
                    val videos = rubyVidExtractor.videosFromUrl(finalUrl, serverName)
                    android.util.Log.d("Anichin", "RubyVidHub videos: ${videos.size}")
                    videoList.addAll(videos)
                }

                // ✨ Rumble (improved extractor)
                finalUrl.contains("rumble", ignoreCase = true) -> {
                    android.util.Log.d("Anichin", "Extracting Rumble (new extractor)")
                    val videos = rumbleExtractor.videosFromUrl(finalUrl, serverName)
                    android.util.Log.d("Anichin", "Rumble videos: ${videos.size}")

                    // Fallback ke old method jika gagal
                    if (videos.isEmpty()) {
                        android.util.Log.d("Anichin", "Fallback to old Rumble extraction")
                        videoList.addAll(extractRumbleLegacy(finalUrl, serverName))
                    } else {
                        videoList.addAll(videos)
                    }
                }

                // Existing extractors (with case-insensitive)
                finalUrl.contains("ok.ru", ignoreCase = true) || 
                finalUrl.contains("odnoklassniki", ignoreCase = true) -> {
                    android.util.Log.d("Anichin", "Extracting OK.ru")
                    val videos = okruExtractor.videosFromUrl(finalUrl, "$serverName - ")
                    android.util.Log.d("Anichin", "OK.ru videos: ${videos.size}")
                    videoList.addAll(videos)
                }

                finalUrl.contains("dailymotion", ignoreCase = true) -> {
                    android.util.Log.d("Anichin", "Extracting Dailymotion")
                    val videos = dailymotionExtractor.videosFromUrl(finalUrl, prefix = "$serverName - ")
                    android.util.Log.d("Anichin", "Dailymotion videos: ${videos.size}")
                    videoList.addAll(videos)
                }

                finalUrl.contains("drive.google", ignoreCase = true) || 
                finalUrl.contains("drive.usercontent.google", ignoreCase = true) -> {
                    android.util.Log.d("Anichin", "Extracting Google Drive")
                    val videos = googleDriveExtractor.videosFromUrl(finalUrl, "$serverName - ")
                    android.util.Log.d("Anichin", "GDrive videos: ${videos.size}")
                    videoList.addAll(videos)
                }

                finalUrl.contains(".m3u8", ignoreCase = true) -> {
                    android.util.Log.d("Anichin", "Extracting HLS")
                    val videos = playlistUtils.extractFromHls(finalUrl, baseUrl)
                    android.util.Log.d("Anichin", "HLS videos: ${videos.size}")
                    videoList.addAll(videos)
                }

                else -> {
                    android.util.Log.d("Anichin", "Generic iframe: $finalUrl")
                    videoList.add(Video(finalUrl, serverName, finalUrl))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Anichin", "Extraction failed for $url: ${e.message}")
            e.printStackTrace()
            videoList.add(Video(url, "$serverName (Error)", url))
        }
    }

    /**
     * Legacy Rumble extraction (fallback)
     */
    private fun extractRumbleLegacy(url: String, quality: String): List<Video> {
        return try {
            val document = client.newCall(GET(url, headers)).execute().asJsoup()

            // Find m3u8 URL in scripts
            val m3u8Url = document.select("script").mapNotNull { script ->
                val scriptContent = script.data()
                Regex("""["'](https://[^"']*\.m3u8[^"']*)["']""")
                    .findAll(scriptContent)
                    .map { it.groupValues[1] }
                    .firstOrNull()
            }.firstOrNull()

            if (m3u8Url != null) {
                android.util.Log.d("Anichin", "Found Rumble m3u8: $m3u8Url")
                playlistUtils.extractFromHls(
                    playlistUrl = m3u8Url,
                    referer = url,
                    videoNameGen = { q -> "$quality - $q" },
                )
            } else {
                android.util.Log.w("Anichin", "Rumble m3u8 not found, using iframe")
                listOf(Video(url, "$quality (Rumble)", url))
            }
        } catch (e: Exception) {
            android.util.Log.e("Anichin", "Rumble extraction error: ${e.message}")
            e.printStackTrace()
            listOf(Video(url, "$quality (Rumble - Error)", url))
        }
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("NOTE: Filters are ignored if using text search!"),
            AnimeFilter.Separator(),
            AnichinFilters.GenreFilter(),
        )
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // ===== Network Settings =====
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = "Base URL"
            summary = "Default: $PREF_BASE_URL_DEFAULT\nCurrent: %s\n\nGanti jika domain berubah (misal: anichin.watch → anichin.cc)"
            setDefaultValue(PREF_BASE_URL_DEFAULT)
            dialogTitle = "Enter Base URL"
            dialogMessage = "Format: https://domain.com (tanpa trailing slash)"

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
            summary = "Custom User Agent untuk bypass block\n\nDefault: Chrome Windows\nCurrent: %s"
            setDefaultValue(PREF_USER_AGENT_DEFAULT)
            dialogTitle = "Enter User Agent"
            dialogMessage = "Gunakan User Agent dari browser yang tidak di-block"

            setOnPreferenceChangeListener { _, newValue ->
                val ua = newValue as String
                preferences.edit().putString(key, ua).commit()
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_CLOUDFLARE_KEY
            title = "CloudFlare Bypass"
            summary = "Enable CloudFlare interceptor (on by default)\n\nMatikan jika menyebabkan loading lambat"
            setDefaultValue(PREF_CLOUDFLARE_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(key, newValue as Boolean).commit()
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_TIMEOUT_KEY
            title = "Network Timeout (seconds)"
            summary = "Timeout untuk network requests\n\nDefault: 90 detik\nCurrent: %s detik"
            setDefaultValue(PREF_TIMEOUT_DEFAULT)
            dialogTitle = "Enter Timeout"
            dialogMessage = "Dalam detik (contoh: 60, 90, 120)"

            setOnPreferenceChangeListener { _, newValue ->
                val timeout = (newValue as String).toLongOrNull()
                if (timeout != null && timeout > 0) {
                    preferences.edit().putString(key, newValue).commit()
                } else {
                    false
                }
            }
        }.also(screen::addPreference)

        // ===== Video Settings =====
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

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SKIP_ADS_KEY
            title = "Skip [ADS] Servers"
            summary = "Otomatis skip server dengan label [ADS]"
            setDefaultValue(PREF_SKIP_ADS_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(key, newValue as Boolean).commit()
            }
        }.also(screen::addPreference)
    }

    // ============================= Utilities ==============================

    companion object {
        // Network Settings
        private const val PREF_BASE_URL_KEY = "base_url"
        private const val PREF_BASE_URL_DEFAULT = "https://anichin.watch"

        private const val PREF_USER_AGENT_KEY = "user_agent"
        private const val PREF_USER_AGENT_DEFAULT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        private const val PREF_CLOUDFLARE_KEY = "cloudflare_enabled"
        private const val PREF_CLOUDFLARE_DEFAULT = true

        private const val PREF_TIMEOUT_KEY = "network_timeout"
        private const val PREF_TIMEOUT_DEFAULT = "90"

        // Video Settings
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
        private val PREF_QUALITY_VALUES = arrayOf("1080p", "720p", "480p", "360p")

        private const val PREF_SKIP_ADS_KEY = "skip_ads_servers"
        private const val PREF_SKIP_ADS_DEFAULT = true
    }
}
