package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.cloudflareinterceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.doodstreamextractor.DoodstreamExtractor
import eu.kanade.tachiyomi.lib.googledriveextractor.GoogleDriveExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
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
    override val baseUrl = "https://anichin.watch"
    override val lang = "id"
    override val supportsLatest = true

    private val cloudflareInterceptor by lazy {
        CloudflareInterceptor(network.client)
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(cloudflareInterceptor)
        .connectTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
        add("Referer", baseUrl)
        add("DNT", "1")
        add("Connection", "keep-alive")
        add("Upgrade-Insecure-Requests", "1")
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val okruExtractor by lazy { OkruExtractor(client) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val googleDriveExtractor by lazy { GoogleDriveExtractor(client, headers) }
    private val doodstreamExtractor by lazy { DoodstreamExtractor(client) }
    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

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

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/ongoing/?page=$page", headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

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

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1.entry-title")?.text() ?: ""
            thumbnail_url = document.selectFirst("div.thumb img")?.attr("src")
            genre = document.select("div.genxed a").joinToString { it.text() }
            status = when {
                document.select("div.status").text().contains("Ongoing", true) -> SAnime.ONGOING
                document.select("div.status").text().contains("Completed", true) -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }
            description = buildString {
                document.selectFirst("div.desc")?.text()?.let { append(it) }
            }
        }
    }

    override fun episodeListSelector(): String = "div.eplister ul li"

    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            val episodeUrl = element.selectFirst("a")!!.attr("href")
            setUrlWithoutDomain(episodeUrl)
            val rawName = element.selectFirst("span.epcur")?.text() ?: element.selectFirst("a")?.text() ?: "Episode"
            name = if (rawName.length > 80) rawName.take(77) + "..." else rawName
            episode_number = rawName.filter { it.isDigit() }.toFloatOrNull() ?: 0f
            date_upload = System.currentTimeMillis()
        }
    }

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()
        android.util.Log.d("Anichin", "=== VIDEO PARSE START ===")
        android.util.Log.d("Anichin", "Page URL: ${response.request.url}")
        parseStreamingServers(document, videoList)
        parseDownloadMirrorLinks(document, videoList)
        android.util.Log.d("Anichin", "=== TOTAL VIDEOS: ${videoList.size} ===")
        val uniqueVideos = videoList.distinctBy { it.videoUrl }
        if (uniqueVideos.size < videoList.size) {
            android.util.Log.d("Anichin", "Removed ${videoList.size - uniqueVideos.size} duplicates")
        }
        return uniqueVideos.ifEmpty {
            listOf(Video(response.request.url.toString(), "Open in WebView", response.request.url.toString()))
        }
    }

    private fun parseStreamingServers(document: Document, videoList: MutableList<Video>) {
        android.util.Log.d("Anichin", "=== PARSING STREAMING SERVERS ===")
        document.select("select.mirror option").forEachIndexed { index, option ->
            val serverValue = option.attr("value")
            val serverName = option.text().trim()
            android.util.Log.d("Anichin", "[Streaming $index] Server: $serverName")
            if (serverValue.isNotEmpty() && !serverName.contains("Select", true)) {
                try {
                    val decodedHtml = try {
                        String(android.util.Base64.decode(serverValue, android.util.Base64.DEFAULT))
                    } catch (e: Exception) {
                        android.util.Log.e("Anichin", "Base64 failed: ${e.message}")
                        serverValue
                    }
                    val iframeSrc = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(decodedHtml)?.groupValues?.get(1)
                    if (iframeSrc != null) {
                        val cleanUrl = when {
                            iframeSrc.startsWith("//") -> "https:$iframeSrc"
                            iframeSrc.startsWith("http") -> iframeSrc
                            else -> iframeSrc
                        }
                        android.util.Log.d("Anichin", "[Streaming $index] Iframe: $cleanUrl")
                        extractVideoFromUrl(cleanUrl, videoList, serverName)
                    } else {
                        extractVideoFromUrl(serverValue, videoList, "$serverName (Direct)")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Anichin", "[Streaming $index] Error: ${e.message}")
                }
            }
        }
    }

    private fun parseDownloadMirrorLinks(document: Document, videoList: MutableList<Video>) {
        android.util.Log.d("Anichin", "=== PARSING DOWNLOAD/MIRROR LINKS ===")
        val downloadSelectors = listOf(
            "div.download",
            "div.download-links",
            "div.mirror-links",
            "div.links-download",
            "div#download",
            "div#mirror",
            "section.download",
            "section.mirror",
        )
        for (selector in downloadSelectors) {
            val elements = document.select(selector)
            if (elements.isNotEmpty()) {
                android.util.Log.d("Anichin", "Found section: $selector (${elements.size})")
                elements.forEach { section ->
                    parseLinksInSection(section, "Download", videoList)
                }
            }
        }
        val textSelectors = listOf(
            "div:contains(Download)",
            "div:contains(Unduh)",
            "div:contains(Mirror)",
            "div:contains(Alternatif)",
            "div:contains(Link)",
            "div:contains(Sumber)",
        )
        for (selector in textSelectors) {
            val elements = document.select(selector)
            elements.forEach { element ->
                if (element.text().contains(Regex("""(?i)download|unduh|mirror|alternatif"""))) {
                    parseLinksInSection(element, "Text", videoList)
                }
            }
        }
        val directPatterns = listOf(
            "a[href*='dsvplay.com']",
            "a[href*='myvidplay.com']",
            "a[href*='doodstream']",
            "a[href*='dood.']",
            "a[href*='terabox.com']",
            "a[href*='1024tera.com']",
            "a[href*='drive.google.com']",
            "a[href*='docs.google.com']",
        )
        directPatterns.forEach { pattern ->
            val links = document.select(pattern)
            if (links.isNotEmpty()) {
                android.util.Log.d("Anichin", "Found ${links.size} links: $pattern")
                links.forEach { link ->
                    val url = link.attr("href")
                    val text = link.text().trim()
                    val displayText = if (text.isNotBlank()) text else "Mirror Link"
                    if (url.isNotBlank()) {
                        android.util.Log.d("Anichin", "[Direct] '$displayText' -> $url")
                        processDownloadMirrorLink(url, displayText, videoList)
                    }
                }
            }
        }
    }

    private fun parseLinksInSection(section: Element, sectionType: String, videoList: MutableList<Video>) {
        val links = section.select("a[href]")
        android.util.Log.d("Anichin", "Parsing $sectionType: ${links.size} links")
        links.forEachIndexed { index, link ->
            val url = link.attr("href")
            val text = link.text().trim()
            if (url.isNotBlank()) {
                val displayText = if (text.isNotBlank()) text else "$sectionType Link ${index + 1}"
                android.util.Log.d("Anichin", "[$sectionType $index] '$displayText' -> $url")
                processDownloadMirrorLink(url, displayText, videoList)
            }
        }
        section.select("button[onclick], span[onclick]").forEach { element ->
            val onclick = element.attr("onclick")
            if (onclick.isNotBlank()) {
                val urlPattern = Regex("""(https?://[^'"]+)""")
                val match = urlPattern.find(onclick)
                if (match != null) {
                    val url = match.groupValues[1]
                    val text = element.text().trim()
                    val displayText = if (text.isNotBlank()) text else "JS Link"
                    android.util.Log.d("Anichin", "[JS] '$displayText' -> $url")
                    processDownloadMirrorLink(url, displayText, videoList)
                }
            }
        }
    }

    private fun processDownloadMirrorLink(url: String, text: String, videoList: MutableList<Video>) {
        try {
            val cleanUrl = normalizeUrl(url)
            if (cleanUrl.isBlank()) return
            android.util.Log.d("Anichin", "Processing: '$text' -> $cleanUrl")
            when {
                cleanUrl.contains("dsvplay.com") || cleanUrl.contains("myvidplay.com") ||
                cleanUrl.contains("doodstream") || cleanUrl.contains("/d/") -> {
                    cleanUrl.contains("drive.google.com") || cleanUrl.contains("docs.google.com") -> {
                        android.util.Log.d("Anichin", "Detected Google Drive")
                        try {
                            val videos = googleDriveExtractor.videosFromUrl(cleanUrl, "$text - ")
                            android.util.Log.d("Anichin", "GDrive: ${videos.size} videos")
                            videoList.addAll(videos)
                        } catch (e: Exception) {
                            android.util.Log.e("Anichin", "GDrive failed: ${e.message}")
                            videoList.add(Video(cleanUrl, "$text (Google Drive)", cleanUrl))
                        }
                    }
                    cleanUrl.contains("terabox.com") || cleanUrl.contains("1024tera.com") -> {
                        android.util.Log.d("Anichin", "Detected Terabox")
                        videoList.add(Video(cleanUrl, "$text (Terabox)", cleanUrl))
                    }
                    cleanUrl.contains(".mp4") || cleanUrl.contains(".m3u8") ||
                    cleanUrl.contains(".mkv") || cleanUrl.contains(".webm") -> {
                        android.util.Log.d("Anichin", "Detected direct video")
                        videoList.add(Video(cleanUrl, "$text (Direct)", cleanUrl))
                    }
                    else -> {
                        android.util.Log.d("Anichin", "Generic mirror")
                        extractVideoFromUrl(cleanUrl, videoList, text)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Anichin", "Failed to process '$text': ${e.message}")
            }
        }

        private fun normalizeUrl(url: String): String {
            if (url.isBlank()) return ""
            return when {
                url.startsWith("//") -> "https:$url"
                url.startsWith("/") && url.length > 1 -> "$baseUrl$url"
                url.startsWith("#") || url.startsWith("javascript:") -> ""
                url.startsWith("http") -> url
                else -> if (url.contains(".") && !url.contains(" ")) "https://$url" else ""
            }
        }

        private fun extractVideoFromUrl(url: String, videoList: MutableList<Video>, serverName: String) {
            try {
                android.util.Log.d("Anichin", "Extracting: $url (Server: $serverName)")
                when {
                    url.contains("ok.ru") || url.contains("odnoklassniki") -> {
                        android.util.Log.d("Anichin", "Extracting OK.ru")
                        val videos = okruExtractor.videosFromUrl(url, "$serverName - ")
                        android.util.Log.d("Anichin", "OK.ru: ${videos.size}")
                        videoList.addAll(videos)
                    }
                    url.contains("dailymotion") -> {
                        android.util.Log.d("Anichin", "Extracting Dailymotion")
                        val videos = dailymotionExtractor.videosFromUrl(url, prefix = "$serverName - ")
                        android.util.Log.d("Anichin", "Dailymotion: ${videos.size}")
                        videoList.addAll(videos)
                    }
                    url.contains("drive.google") || url.contains("drive.usercontent.google") -> {
                        android.util.Log.d("Anichin", "Extracting Google Drive")
                        val videos = googleDriveExtractor.videosFromUrl(url, "$serverName - ")
                        android.util.Log.d("Anichin", "GDrive: ${videos.size}")
                        videoList.addAll(videos)
                    }
                    url.contains("dsvplay.com") || url.contains("myvidplay.com") ||
                    url.contains("doodstream") || url.contains("/d/") -> {
                        android.util.Log.d("Anichin", "Extracting Doodstream")
                        val videos = doodstreamExtractor.videosFromUrl(url, "$serverName - ")
                        android.util.Log.d("Anichin", "Doodstream: ${videos.size}")
                        videoList.addAll(videos)
                    }
                }
                url.contains(".m3u8") -> {
                    android.util.Log.d("Anichin", "Extracting HLS")
                    val videos = playlistUtils.extractFromHls(url, baseUrl)
                    android.util.Log.d("Anichin", "HLS: ${videos.size}")
                    videoList.addAll(videos)
                }
                else -> {
                    android.util.Log.d("Anichin", "Generic URL")
                    videoList.add(Video(url, serverName, url))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Anichin", "Extraction failed: ${e.message}")
            videoList.add(Video(url, "$serverName (Error)", url))
        }
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()
    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()
    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("NOTE: Filters are ignored if using text search!"),
            AnimeFilter.Separator(),
            AnichinFilters.GenreFilter(),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
        private val PREF_QUALITY_VALUES = arrayOf("1080p", "720p", "480p", "360p")
    }
}
