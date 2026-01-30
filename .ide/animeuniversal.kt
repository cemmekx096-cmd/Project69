package eu.kanade.tachiyomi.lib.animeuniversal

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Universal Anime Scraper
 * 
 * Generic scraper yang bisa dipakai untuk banyak anime websites
 * Dengan multiple fallback patterns untuk maximize compatibility
 */
class AnimeUniversalScraper(private val client: OkHttpClient) {

    /**
     * Parse anime details with fallback patterns
     */
    fun parseAnimeDetails(document: Document): SAnime {
        return SAnime.create().apply {
            title = parseTitle(document)
            thumbnail_url = parseThumbnail(document)
            description = parseDescription(document)
            genre = parseGenres(document)
            status = parseStatus(document)
            author = parseStudio(document)
        }
    }

    /**
     * Parse episode list with fallback patterns
     */
    fun parseEpisodeList(document: Document, baseUrl: String): List<SEpisode> {
        val episodes = mutableListOf<SEpisode>()

        // Try multiple selectors
        val episodeElements = trySelectEpisodes(document)

        episodeElements.forEachIndexed { index, element ->
            try {
                val episode = parseEpisode(element, index, baseUrl)
                if (episode.url.isNotEmpty()) {
                    episodes.add(episode)
                }
            } catch (e: Exception) {
                // Skip failed episodes
            }
        }

        return episodes.reversed() // Latest first
    }

    // ==================== Title Parsing ====================

    private fun parseTitle(document: Document): String {
        val titleSelectors = listOf(
            "h1.entry-title",
            "h1.title",
            "h1",
            ".anime-title",
            ".series-title",
            "meta[property=og:title]",
            "title",
        )

        for (selector in titleSelectors) {
            val title = if (selector.startsWith("meta")) {
                document.selectFirst(selector)?.attr("content")
            } else if (selector == "title") {
                document.title()
            } else {
                document.selectFirst(selector)?.text()
            }

            if (!title.isNullOrBlank()) {
                return cleanTitle(title)
            }
        }

        return "Unknown Title"
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""\s*-\s*Anichin.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\|\s*.*$"""), "")
            .trim()
    }

    // ==================== Thumbnail Parsing ====================

    private fun parseThumbnail(document: Document): String? {
        val thumbnailSelectors = listOf(
            "div.thumb img",
            ".anime-cover img",
            ".poster img",
            "meta[property=og:image]",
            "img[itemprop=image]",
            ".entry-content img:first-of-type",
        )

        for (selector in thumbnailSelectors) {
            val thumbnail = if (selector.startsWith("meta")) {
                document.selectFirst(selector)?.attr("content")
            } else {
                document.selectFirst(selector)?.let { img ->
                    img.attr("src").ifEmpty { img.attr("data-src") }
                }
            }

            if (!thumbnail.isNullOrBlank() && thumbnail.startsWith("http")) {
                return thumbnail
            }
        }

        return null
    }

    // ==================== Description Parsing ====================

    private fun parseDescription(document: Document): String? {
        val descSelectors = listOf(
            "div.desc",
            ".synopsis",
            ".description",
            ".summary",
            "[itemprop=description]",
            "meta[property=og:description]",
            "meta[name=description]",
        )

        for (selector in descSelectors) {
            val desc = if (selector.startsWith("meta")) {
                document.selectFirst(selector)?.attr("content")
            } else {
                document.selectFirst(selector)?.text()
            }

            if (!desc.isNullOrBlank()) {
                return cleanDescription(desc)
            }
        }

        return null
    }

    private fun cleanDescription(desc: String): String {
        return desc
            .replace(Regex("""hardsub.*?video\.?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""Watch streaming.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    // ==================== Genre Parsing ====================

    private fun parseGenres(document: Document): String? {
        val genreSelectors = listOf(
            "div.genxed a",
            ".genres a",
            ".genre a",
            "[rel=category tag]",
            ".info-genre a",
        )

        for (selector in genreSelectors) {
            val genres = document.select(selector)
                .mapNotNull { it.text() }
                .filter { it.isNotBlank() }

            if (genres.isNotEmpty()) {
                return genres.joinToString(", ")
            }
        }

        return null
    }

    // ==================== Status Parsing ====================

    private fun parseStatus(document: Document): Int {
        val statusSelectors = listOf(
            "div.status",
            ".anime-status",
            ".info-status",
            "span.status",
        )

        for (selector in statusSelectors) {
            val statusText = document.selectFirst(selector)?.text()?.lowercase() ?: continue

            return when {
                statusText.contains("ongoing") -> SAnime.ONGOING
                statusText.contains("completed") -> SAnime.COMPLETED
                else -> continue
            }
        }

        return SAnime.UNKNOWN
    }

    // ==================== Studio Parsing ====================

    private fun parseStudio(document: Document): String? {
        val studioSelectors = listOf(
            "span.studio",
            ".anime-studio",
            ".info-studio",
        )

        for (selector in studioSelectors) {
            val studio = document.selectFirst(selector)?.text()
            if (!studio.isNullOrBlank()) {
                return studio
            }
        }

        return null
    }

    // ==================== Episode List Parsing ====================

    private fun trySelectEpisodes(document: Document): List<Element> {
        val episodeSelectors = listOf(
            "div.eplister ul li",
            ".episode-list li",
            ".episodelist li",
            ".eps-list li",
            "ul.episodios li",
            "[class*=episode] li",
            "div.bixbox ul li",
        )

        for (selector in episodeSelectors) {
            val elements = document.select(selector)
            if (elements.isNotEmpty()) {
                android.util.Log.d("UniversalScraper", "Found ${elements.size} episodes with: $selector")
                return elements
            }
        }

        android.util.Log.w("UniversalScraper", "No episodes found with any selector")
        return emptyList()
    }

    private fun parseEpisode(element: Element, index: Int, baseUrl: String): SEpisode {
        return SEpisode.create().apply {
            // URL
            url = parseEpisodeUrl(element, baseUrl)

            // Name
            name = parseEpisodeName(element, index)

            // Episode number
            episode_number = parseEpisodeNumber(name)

            // Date
            date_upload = parseEpisodeDate(element)
        }
    }

    private fun parseEpisodeUrl(element: Element, baseUrl: String): String {
        val urlSelectors = listOf("a", "[href]")

        for (selector in urlSelectors) {
            val href = element.selectFirst(selector)?.attr("href")
            if (!href.isNullOrBlank()) {
                return if (href.startsWith("http")) {
                    href
                } else if (href.startsWith("/")) {
                    "$baseUrl$href"
                } else {
                    "$baseUrl/$href"
                }
            }
        }

        return ""
    }

    private fun parseEpisodeName(element: Element, index: Int): String {
        val nameSelectors = listOf(
            "span.epcur",
            ".episode-title",
            ".eptitle",
            "a",
        )

        for (selector in nameSelectors) {
            val name = element.selectFirst(selector)?.text()
            if (!name.isNullOrBlank()) {
                return if (name.length > 80) {
                    name.take(77) + "..."
                } else {
                    name
                }
            }
        }

        return "Episode ${index + 1}"
    }

    private fun parseEpisodeNumber(name: String): Float {
        val numberPatterns = listOf(
            Regex("""Episode\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""Ep\.?\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:\.\d+)?)"""),
        )

        for (pattern in numberPatterns) {
            pattern.find(name)?.groupValues?.get(1)?.let { number ->
                return number.toFloatOrNull() ?: 0f
            }
        }

        return name.filter { it.isDigit() }.toFloatOrNull() ?: 0f
    }

    private fun parseEpisodeDate(element: Element): Long {
        // Try to parse date from various selectors
        val dateSelectors = listOf(
            "span.date",
            ".episode-date",
            "time",
        )

        for (selector in dateSelectors) {
            val dateText = element.selectFirst(selector)?.text()
            if (!dateText.isNullOrBlank()) {
                // Here you could add date parsing logic
                // For now, just return current time
                return System.currentTimeMillis()
            }
        }

        return System.currentTimeMillis()
    }

    // ==================== Popular/Latest Anime ====================

    /**
     * Parse popular/latest anime list
     */
    fun parseAnimeList(document: Document, baseUrl: String): List<SAnime> {
        val animeList = mutableListOf<SAnime>()

        val animeSelectors = listOf(
            "div.listupd article.bs",
            ".anime-list article",
            ".post-show li",
            ".items article",
        )

        for (selector in animeSelectors) {
            val elements = document.select(selector)
            if (elements.isEmpty()) continue

            android.util.Log.d("UniversalScraper", "Found ${elements.size} anime with: $selector")

            elements.forEach { element ->
                try {
                    val anime = parseAnimeFromElement(element, baseUrl)
                    if (anime.url.isNotEmpty()) {
                        animeList.add(anime)
                    }
                } catch (e: Exception) {
                    // Skip failed items
                }
            }

            if (animeList.isNotEmpty()) break
        }

        return animeList
    }

    private fun parseAnimeFromElement(element: Element, baseUrl: String): SAnime {
        return SAnime.create().apply {
            // URL
            val linkSelectors = listOf("div.bsx > a", "a.tip", "a:first-child", "a")
            for (selector in linkSelectors) {
                val href = element.selectFirst(selector)?.attr("href")
                if (!href.isNullOrBlank()) {
                    setUrlWithoutDomain(href)
                    break
                }
            }

            // Thumbnail
            val imgSelectors = listOf("div.bsx img", "img", ".poster img")
            for (selector in imgSelectors) {
                val img = element.selectFirst(selector)
                val src = img?.attr("src")?.ifEmpty { img.attr("data-src") }
                if (!src.isNullOrBlank()) {
                    thumbnail_url = src
                    break
                }
            }

            // Title
            val titleSelectors = listOf(
                "div.bsx a[title]",
                ".title",
                "h2",
                "h3",
                "a",
            )
            for (selector in titleSelectors) {
                val titleText = if (selector.contains("[title]")) {
                    element.selectFirst(selector)?.attr("title")
                } else {
                    element.selectFirst(selector)?.text()
                }
                if (!titleText.isNullOrBlank()) {
                    title = titleText
                    break
                }
            }

            if (title.isNullOrEmpty()) {
                title = "Unknown"
            }
        }
    }
}
