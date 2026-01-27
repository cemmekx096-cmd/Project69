package eu.kanade.tachiyomi.animeextension.id.anichin

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.lib.dailymotionextractor.DailymotionExtractor
import eu.kanade.tachiyomi.lib.googledriveextractor.GoogleDriveExtractor
import eu.kanade.tachiyomi.lib.okruextractor.OkruExtractor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Anichin : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Anichin"
    override val baseUrl = "https://anichin.cafe"
    override val lang = "id"
    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", MODE_PRIVATE)
    }

    private val okruExtractor by lazy { OkruExtractor(client) }
    private val dailymotionExtractor by lazy { DailymotionExtractor(client, headers) }
    private val googleDriveExtractor by lazy { GoogleDriveExtractor(client, headers) }

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/ongoing/?page=$page", headers)
    }

    override fun popularAnimeSelector(): String = "div.listupd article.bs"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            element.selectFirst("div.bsx > a")?.attr("href")?.let { setUrlWithoutDomain(it) }

            element.selectFirst("div.bsx img")?.let { img ->
                val candidate = img.attr("data-src").ifEmpty { img.attr("src") }.ifEmpty { null }
                thumbnail_url = candidate
            }

            title = element.selectFirst("div.bsx a")?.attr("title")
                ?: element.selectFirst("div.bsx a")?.text()
                ?: ""
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

        return when {
            query.isNotEmpty() -> GET("$baseUrl/?s=$query&page=$page", headers)
            params.genre.isNotEmpty() -> GET("$baseUrl/genres/${params.genre}/?page=$page", headers)
            else -> popularAnimeRequest(page)
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            title = document.selectFirst("h1.entry-title")?.text() ?: ""

            document.selectFirst("div.thumb img")?.let { img ->
                val src = img.attr("data-src").ifEmpty { img.attr("src") }.ifEmpty { null }
                thumbnail_url = src
            }

            genre = document.select("div.genxed a").joinToString(", ") { it.text() }

            val statusText = document.selectFirst("div.status")?.text().orEmpty()
            status = when {
                statusText.contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
                statusText.contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }

            description = document.selectFirst("div.desc")?.text() ?: ""
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector(): String = "div.eplister ul li"

    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            element.selectFirst("a")?.attr("href")?.let { setUrlWithoutDomain(it) }

            val nameCandidate = element.selectFirst("span.epcur")?.text()
                ?: element.selectFirst("a")?.text()
                ?: "Episode"

            name = nameCandidate

            // Support decimal (12.5) as well as simple integers
            val match = Regex("""\d+(\.\d+)?""").find(nameCandidate)
            episode_number = match?.value?.toFloatOrNull() ?: 0f

            date_upload = System.currentTimeMillis()
        }
    }

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        // 1) Check select.mirror options (primary)
        document.select("select.mirror option").forEach { option ->
            val serverValue = option.attr("value").orEmpty()
            val serverName = option.text().trim().ifEmpty { "Server" }
            val dataIndex = option.attr("data-index").ifEmpty { "" }

            if (serverValue.isNotEmpty() && !serverName.contains("select", ignoreCase = true)) {
                try {
                    // Try base64 decode; if decoded contains iframe -> parse iframe src,
                    // otherwise treat as direct URL.
                    var decoded = serverValue
                    try {
                        val bytes = Base64.decode(serverValue, Base64.DEFAULT)
                        val asStr = String(bytes)
                        if (asStr.contains("<iframe", ignoreCase = true) || asStr.contains("src=", ignoreCase = true)) {
                            decoded = asStr
                        }
                    } catch (e: Exception) {
                        // not base64 — keep original
                    }

                    val extractedUrl = AnichinFactory.extractIframeSrcOrUrl(decoded)
                        ?: serverValue

                    val label = if (dataIndex.isNotEmpty()) "$serverName #$dataIndex" else serverName
                    Log.d("Anichin", "Found server: $label -> $extractedUrl")

                    extractVideoFromUrl(extractedUrl, videoList, label)
                } catch (e: Exception) {
                    Log.e("Anichin", "Failed parsing server option '$serverName': ${e.message}")
                }
            }
        }

        // 2) player-embed iframe fallback
        if (videoList.isEmpty()) {
            val iframeSrc = document.selectFirst("div.player-embed iframe, div#pembed iframe")?.attr("src")
            iframeSrc?.takeIf { it.isNotEmpty() }?.let {
                val clean = if (it.startsWith("//")) "https:$it" else it
                extractVideoFromUrl(clean, videoList, "Main Player")
            }
        }

        // 3) Generic iframe fallback
        if (videoList.isEmpty()) {
            document.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotEmpty()) {
                    val clean = if (src.startsWith("//")) "https:$src" else src
                    extractVideoFromUrl(clean, videoList, "Iframe Fallback")
                }
            }
        }

        // 4) Download links (Terabox / Mirrored) — parse soraurlx blocks
        document.select("div.soraurlx").forEach { block ->
            val quality = block.selectFirst("strong")?.text()?.trim() ?: "Download"
            block.select("a[href]").forEach { a ->
                val href = a.attr("href")
                val hostLabel = when {
                    href.contains("terabox", ignoreCase = true) -> "Terabox"
                    href.contains("mirrored.to", ignoreCase = true) -> "Mirrored"
                    else -> "Download"
                }
                videoList.add(Video(href, "$quality - $hostLabel (Download)", href))
            }
        }

        return videoList.ifEmpty {
            // Fallback: open page in WebView
            listOf(Video(response.request.url.toString(), "Open in WebView (Tap to Play)", response.request.url.toString()))
        }
    }

    private fun extractVideoFromUrl(url: String, videoList: MutableList<Video>, serverName: String) {
        try {
            val lower = url.lowercase()
            when {
                "ok.ru" in lower || "odnoklassniki" in lower -> {
                    val videos = okruExtractor.videosFromUrl(url, "$serverName - ")
                    videos.forEach { Log.d("Anichin", "OK.ru: ${it.quality} -> ${it.url}") }
                    videoList.addAll(videos)
                }
                "dailymotion" in lower || "dai.ly" in lower -> {
                    val videos = dailymotionExtractor.videosFromUrl(url, prefix = "$serverName - ")
                    videos.forEach { Log.d("Anichin", "Dailymotion: ${it.quality} -> ${it.url}") }
                    videoList.addAll(videos)
                }
                "drive.google" in lower || "drive.googleusercontent" in lower || "docs.google" in lower -> {
                    val videos = googleDriveExtractor.videosFromUrl(url, "$serverName - ")
                    videos.forEach { Log.d("Anichin", "GDrive: ${it.quality} -> ${it.url}") }
                    videoList.addAll(videos)
                }
                "rumble" in lower -> {
                    Log.d("Anichin", "Rumble iframe: $url")
                    videoList.add(Video(url, "$serverName (Rumble)", url))
                }
                "anichinv2" in lower || ("anichin" in lower && lower.contains(".m3u8")) -> {
                    // Premium HLS server or direct HLS URL
                    videoList.add(Video(url, "$serverName (HLS)", url))
                }
                // Mirrored / Terabox downloads (treated as downloadable links)
                "terabox" in lower || "mirrored.to" in lower -> {
                    videoList.add(Video(url, "$serverName (Download)", url))
                }
                else -> {
                    // Generic server: may itself be an embeddable player. Add as-is.
                    Log.d("Anichin", "Generic server: $url")
                    // Try to parse if the url itself contains a base64-encoded iframe string
                    val iframeSrc = AnichinFactory.extractIframeSrcOrUrl(url)
                    val finalUrl = iframeSrc ?: url
                    videoList.add(Video(finalUrl, serverName, finalUrl))
                }
            }
        } catch (e: Exception) {
            Log.e("Anichin", "Extraction failed for $url: ${e.message}")
            // Add fallback entry so user can open page or link
            videoList.add(Video(url, "$serverName (Fallback)", url))
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
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred quality"
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s"

            // reflect saved preference
            value = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)

            setOnPreferenceChangeListener { pref, newValue ->
                val selected = newValue as String
                preferences.edit().putString(key, selected).apply()
                pref.summary = selected
                true
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
