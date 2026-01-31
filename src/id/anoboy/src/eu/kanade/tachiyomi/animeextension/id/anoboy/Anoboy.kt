package eu.kanade.tachiyomi.animeextension.id.anoboy

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
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

class Anoboy : ConfigurableAnimeSource, ParsedAnimeHttpSource() {

    override val name = "Anoboy"

    override val baseUrl = "https://anoboy.be"

    override val lang = "id"

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient = network.client

    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().apply {
            add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            add("Referer", baseUrl)
        }
    }

    // ============================== Popular ===============================
    // Menggunakan order=popular dari anime list

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/anime/?status=&type=&order=popular&page=$page", headers)
    }

    override fun popularAnimeSelector(): String = "div.listupd article.bs"

    override fun popularAnimeFromElement(element: Element): SAnime {
        return SAnime.create().apply {
            // URL dari link utama
            val linkElement = element.selectFirst("div.bsx > a")!!
            setUrlWithoutDomain(linkElement.attr("href"))

            // Thumbnail dari poster
            thumbnail_url = element.selectFirst("div.limit img")?.attr("src")

            // Title dari attribute title atau h2
            title = linkElement.attr("title").takeIf { it.isNotEmpty() }
                ?: element.selectFirst("h2")?.text() ?: ""

            // Extract anime name (remove "Episode X" part if exists)
            if (title.contains("Episode")) {
                title = title.substringBefore("Episode").trim()
            }
        }
    }

    override fun popularAnimeNextPageSelector(): String = "div.pagination a.next"

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/anime/?status=&type=&order=update&page=$page", headers)
    }

    override fun latestUpdatesSelector(): String = popularAnimeSelector()

    override fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularAnimeNextPageSelector()

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = AnoboyFilters.getSearchParameters(filters)

        return when {
            query.isNotEmpty() -> {
                // Text search
                GET("$baseUrl/?s=$query&page=$page", headers)
            }
            params.genres.isNotEmpty() -> {
                // Genre filter
                val genreParams = params.genres.joinToString("&") { "genre[]=$it" }
                GET("$baseUrl/anime?$genreParams&page=$page", headers)
            }
            else -> {
                // Status and Type filter
                val statusParam = if (params.status.isNotEmpty()) "status=${params.status}" else "status="
                val typeParam = if (params.type.isNotEmpty()) "type=${params.type}" else "type="
                val orderParam = "order=${params.order}"
                GET("$baseUrl/anime?$statusParam&$typeParam&$orderParam&page=$page", headers)
            }
        }
    }

    override fun searchAnimeSelector(): String = popularAnimeSelector()

    override fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun searchAnimeNextPageSelector(): String = popularAnimeNextPageSelector()

    // =========================== Anime Details ============================

    override fun animeDetailsParse(document: Document): SAnime {
        return SAnime.create().apply {
            // Title dari h1 atau meta
            title = document.selectFirst("h1.entry-title")?.text()
                ?: document.selectFirst("meta[property=og:title]")?.attr("content")
                ?: ""

            // Remove "Subtitle Indonesia" from title if exists
            if (title.contains("Subtitle Indonesia")) {
                title = title.replace("Subtitle Indonesia", "").trim()
            }

            // Thumbnail
            thumbnail_url = document.selectFirst("div.thumb img")?.attr("src")
                ?: document.selectFirst("meta[property=og:image]")?.attr("content")

            // Genre
            genre = document.select("div.genxed a, span.genre a").joinToString { it.text() }

            // Status
            status = when {
                document.select("div.info-content, span.status").text().contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
                document.select("div.info-content, span.status").text().contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
                else -> SAnime.UNKNOWN
            }

            // Description
            description = buildString {
                document.selectFirst("div.entry-content p, div.desc, div.sinopsis")?.text()?.let { 
                    append(it.trim()) 
                }
            }
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListSelector(): String = "div.listupd article.bs, div.eplister ul li"

    override fun episodeFromElement(element: Element): SEpisode {
        return SEpisode.create().apply {
            // Get episode URL
            val episodeUrl = element.selectFirst("a")!!.attr("href")
            setUrlWithoutDomain(episodeUrl)

            // Get episode name
            val rawName = element.selectFirst("h2, span.epcur, a")?.text() ?: "Episode"

            name = if (rawName.length > 80) {
                rawName.take(77) + "..."
            } else {
                rawName
            }

            // Extract episode number
            episode_number = rawName.replace("[^0-9]".toRegex(), "").toFloatOrNull() ?: 0f

            date_upload = System.currentTimeMillis()
        }
    }

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.asJsoup()
        val videoList = mutableListOf<Video>()

        try {
            // Method 1: Find direct iframe in embed_holder
            val directIframe = document.selectFirst("div#embed_holder iframe, div.player-embed iframe")
            if (directIframe != null) {
                val iframeSrc = directIframe.attr("src")
                if (iframeSrc.isNotEmpty()) {
                    android.util.Log.d("Anoboy", "Direct iframe found: $iframeSrc")
                    videoList.add(Video(iframeSrc, "Blogger - Direct", iframeSrc))
                }
            }

            // Method 2: Find video mirrors (base64 encoded)
            val mirrorSelect = document.select("select.mirror option[value]")
            mirrorSelect.forEach { option ->
                val base64Value = option.attr("value")
                val serverName = option.text().takeIf { it.isNotEmpty() } ?: "Mirror"

                if (base64Value.isNotEmpty() && base64Value != "Select Video Server") {
                    try {
                        // Decode base64
                        val decodedHtml = String(Base64.decode(base64Value, Base64.DEFAULT))

                        // Extract iframe src from decoded HTML
                        val iframeRegex = """src=["']([^"']+)["']""".toRegex()
                        val match = iframeRegex.find(decodedHtml)

                        if (match != null) {
                            val videoUrl = match.groupValues[1]
                            android.util.Log.d("Anoboy", "Decoded video URL: $videoUrl")
                            videoList.add(Video(videoUrl, "Blogger - $serverName", videoUrl))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Anoboy", "Failed to decode base64: ${e.message}")
                    }
                }
            }

            // If no videos found, try to find any iframe
            if (videoList.isEmpty()) {
                document.select("iframe[src]").forEach { iframe ->
                    val src = iframe.attr("src")
                    if (src.contains("blogger.com") || src.contains("video")) {
                        videoList.add(Video(src, "Blogger - Fallback", src))
                    }
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("Anoboy", "Video extraction error: ${e.message}")
            e.printStackTrace()
        }

        // Apply quality preference sorting
        val preferredQuality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT
        return videoList.sortedWith(
            compareBy { video ->
                when {
                    video.quality.contains(preferredQuality, ignoreCase = true) -> 0
                    video.quality.contains("1080p") -> 1
                    video.quality.contains("720p") -> 2
                    video.quality.contains("480p") -> 3
                    else -> 4
                }
            }
        )
    }

    override fun videoListSelector(): String = throw UnsupportedOperationException()

    override fun videoFromElement(element: Element): Video = throw UnsupportedOperationException()

    override fun videoUrlParse(document: Document): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("NOTE: Text search ignores filters!"),
            AnimeFilter.Separator(),
            AnoboyFilters.StatusFilter(),
            AnoboyFilters.TypeFilter(),
            AnoboyFilters.OrderFilter(),
            AnimeFilter.Separator(),
            AnimeFilter.Header("Genre Filter (can select multiple):"),
            AnoboyFilters.GenreFilter(),
        )
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
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

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
        private val PREF_QUALITY_VALUES = arrayOf("1080p", "720p", "480p", "360p")
    }
}
