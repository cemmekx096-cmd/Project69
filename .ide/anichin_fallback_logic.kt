// Add to imports section:
import eu.kanade.tachiyomi.lib.animeuniversal.AnimeUniversalScraper

// Add to class after other extractors:
private val universalScraper by lazy { AnimeUniversalScraper(client) }

// ========== REPLACE animeDetailsParse with fallback logic ==========

override fun animeDetailsParse(document: Document): SAnime {
    return try {
        // Try Anichin-specific parsing first
        parseAnimeDetailsAnichin(document)
    } catch (e: Exception) {
        android.util.Log.w("Anichin", "Anichin-specific parsing failed, trying universal scraper")
        // Fallback to universal scraper
        try {
            universalScraper.parseAnimeDetails(document)
        } catch (e2: Exception) {
            android.util.Log.e("Anichin", "All parsers failed: ${e2.message}")
            // Return minimal anime info
            SAnime.create().apply {
                title = document.title()
                description = "Failed to parse details. Please report this issue."
            }
        }
    }
}

private fun parseAnimeDetailsAnichin(document: Document): SAnime {
    return SAnime.create().apply {
        // Title
        title = document.selectFirst("h1.entry-title")?.text()
            ?: throw Exception("Title not found")

        // Thumbnail
        thumbnail_url = document.selectFirst("div.thumb img")?.attr("src")

        // Genres
        genre = document.select("div.genxed a")
            .joinToString(", ") { it.text() }
            .takeIf { it.isNotBlank() }

        // Status
        status = when {
            document.select("div.status").text().contains("Ongoing", ignoreCase = true) -> SAnime.ONGOING
            document.select("div.status").text().contains("Completed", ignoreCase = true) -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }

        // ✨ IMPROVED DESCRIPTION PARSING
        description = buildString {
            // Get clean description
            val descText = document.selectFirst("div.desc")?.text()
                ?: document.selectFirst("div.entry-content")?.text()
                ?: ""

            if (descText.isNotBlank()) {
                val cleaned = descText
                    // Remove promotional text
                    .replace(Regex("""Watch streaming.*?Anichin\.""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""You can also download.*?video\.""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""hardsub softsub.*?subbed.*?video\.""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""don't forget to watch.*""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""MP4 MKV.*?contained in the video\.""", RegexOption.IGNORE_CASE), "")
                    // Remove quality mentions at end
                    .replace(Regex("""\s*according to your connection.*$""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s*720P 360P 240P 480P.*$""", RegexOption.IGNORE_CASE), "")
                    // Remove extra whitespace
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                append(cleaned)
            }

            // Add studio if available
            document.selectFirst("span.studio")?.text()?.let { studio ->
                if (studio.isNotBlank()) {
                    append("\n\n")
                    append("Studio: $studio")
                }
            }

            // Add aired date if available
            document.selectFirst(".info-date")?.text()?.let { aired ->
                if (aired.isNotBlank()) {
                    append("\n")
                    append("Aired: $aired")
                }
            }
        }.takeIf { it.isNotBlank() }
    }
}

// ========== REPLACE episodeListParse with fallback logic ==========

override fun episodeListParse(response: Response): List<SEpisode> {
    val document = response.asJsoup()

    return try {
        // Try Anichin-specific parsing first
        val episodes = parseEpisodesAnichin(document)
        
        if (episodes.isEmpty()) {
            throw Exception("No episodes found with Anichin parser")
        }
        
        android.util.Log.d("Anichin", "Parsed ${episodes.size} episodes with Anichin parser")
        episodes
    } catch (e: Exception) {
        android.util.Log.w("Anichin", "Anichin parser failed: ${e.message}, trying universal scraper")
        
        // Fallback to universal scraper
        try {
            val episodes = universalScraper.parseEpisodeList(document, baseUrl)
            
            if (episodes.isEmpty()) {
                throw Exception("Universal parser also found no episodes")
            }
            
            android.util.Log.d("Anichin", "Parsed ${episodes.size} episodes with Universal parser")
            episodes
        } catch (e2: Exception) {
            android.util.Log.e("Anichin", "All parsers failed: ${e2.message}")
            // Return empty list instead of crashing
            emptyList()
        }
    }
}

private fun parseEpisodesAnichin(document: Document): List<SEpisode> {
    val episodes = mutableListOf<SEpisode>()

    document.select("div.eplister ul li").forEach { element ->
        try {
            val episode = SEpisode.create().apply {
                val episodeUrl = element.selectFirst("a")?.attr("href")
                    ?: throw Exception("Episode URL not found")
                
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

            episodes.add(episode)
        } catch (e: Exception) {
            // Skip this episode
            android.util.Log.w("Anichin", "Failed to parse episode: ${e.message}")
        }
    }

    return episodes
}

// ========== REPLACE popularAnimeParse with fallback logic ==========

override fun popularAnimeParse(response: Response): AnimesPage {
    val document = response.asJsoup()

    val anime = try {
        // Try Anichin-specific parsing
        document.select(popularAnimeSelector())
            .map { popularAnimeFromElement(it) }
    } catch (e: Exception) {
        android.util.Log.w("Anichin", "Anichin parser failed for popular, trying universal")
        // Fallback to universal
        universalScraper.parseAnimeList(document, baseUrl)
    }

    val hasNextPage = document.selectFirst(popularAnimeNextPageSelector()) != null

    return AnimesPage(anime, hasNextPage)
}

// Same for latestUpdatesParse
override fun latestUpdatesParse(response: Response): AnimesPage {
    val document = response.asJsoup()

    val anime = try {
        document.select(latestUpdatesSelector())
            .map { latestUpdatesFromElement(it) }
    } catch (e: Exception) {
        android.util.Log.w("Anichin", "Anichin parser failed for latest, trying universal")
        universalScraper.parseAnimeList(document, baseUrl)
    }

    val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()) != null

    return AnimesPage(anime, hasNextPage)
}

// Same for searchAnimeParse  
override fun searchAnimeParse(response: Response): AnimesPage {
    val document = response.asJsoup()

    val anime = try {
        document.select(searchAnimeSelector())
            .map { searchAnimeFromElement(it) }
    } catch (e: Exception) {
        android.util.Log.w("Anichin", "Anichin parser failed for search, trying universal")
        universalScraper.parseAnimeList(document, baseUrl)
    }

    val hasNextPage = document.selectFirst(searchAnimeNextPageSelector()) != null

    return AnimesPage(anime, hasNextPage)
}
