package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
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
        get() = preferences.getString(AnichinPreferences.PREF_BASE_URL_KEY, AnichinPreferences.PREF_BASE_URL_DEFAULT)!!

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
            val timeoutSeconds = preferences.getString(AnichinPreferences.PREF_TIMEOUT_KEY, AnichinPreferences.PREF_TIMEOUT_DEFAULT)!!
                .toLongOrNull() ?: 90L

            val builder = network.client.newBuilder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)

            // Add CloudFlare interceptor if enabled
            if (preferences.getBoolean(AnichinPreferences.PREF_CLOUDFLARE_KEY, AnichinPreferences.PREF_CLOUDFLARE_DEFAULT)) {
                builder.addInterceptor(cloudflareInterceptor)
            }

            return builder.build()
        }

    // ✨ Dynamic headers with custom user agent
    override fun headersBuilder(): Headers.Builder {
        val userAgent = preferences.getString(AnichinPreferences.PREF_USER_AGENT_KEY, AnichinPreferences.PREF_USER_AGENT_DEFAULT)!!

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
    private val universalBase64Extractor by lazy { UniversalBase64Extractor(client) }

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
                    if (preferences.getBoolean(AnichinPreferences.PREF_SKIP_ADS_KEY, AnichinPreferences.PREF_SKIP_ADS_DEFAULT) &&
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

        // Sort videos by preferred quality
        val preferredQuality = preferences.getString(
            AnichinPreferences.PREF_QUALITY_KEY,
            AnichinPreferences.PREF_QUALITY_DEFAULT,
        )!!

        val sortedList = videoList.sortedByDescending { video ->
            // Priority 1: Match preferred quality (highest priority)
            if (video.quality.contains(preferredQuality, ignoreCase = true)) {
                1000
            } else {
                // Priority 2: Quality ranking (for non-matching videos)
                when {
                    video.quality.contains("1080p", ignoreCase = true) -> 100
                    video.quality.contains("720p", ignoreCase = true) -> 90
                    video.quality.contains("480p", ignoreCase = true) -> 80
                    video.quality.contains("360p", ignoreCase = true) -> 70
                    else -> 0
                }
            }
        }

        android.util.Log.d("Anichin", "Sorted by preferred quality: $preferredQuality")

        return sortedList.ifEmpty {
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
            
            // Universal Base64 Extractor - backup/additional links
            val additionalVideos = universalBase64Extractor.extractFromUrl(finalUrl, serverName)
            if (additionalVideos.isNotEmpty()) {
                videoList.addAll(additionalVideos)
                android.util.Log.d("Anichin", "UniversalBase64 added ${additionalVideos.size} videos")
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
        AnichinPreferences.setupPreferences(screen, preferences)
    }

    // ============================= Utilities ==============================
}
