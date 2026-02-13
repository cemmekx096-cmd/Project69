package eu.kanade.tachiyomi.animeextension.id.lk21movies

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder

/**
 * Configuration data classes matching scraper-config.json
 */
@Serializable
data class ScraperConfig(
    val version: String,
    val endpoints: EndpointsConfig,
    val selectors: SelectorsConfig,
    val poster_matching: PosterMatchingConfig,
    val data_normalization: DataNormalizationConfig,
    val filtering: FilteringConfig
)

@Serializable
data class EndpointsConfig(
    val popular: EndpointInfo,
    val latest: EndpointInfo,
    val search: EndpointInfo,
    val genre: EndpointInfo,
    val country: EndpointInfo
)

@Serializable
data class EndpointInfo(
    val path: String,
    val page_format: String
)

@Serializable
data class SelectorsConfig(
    val popular: SectionSelector,
    val latest: SectionSelector,
    val search: SectionSelector,
    val detail: DetailSelector,
    val player: PlayerSelector
)

@Serializable
data class SectionSelector(
    val container: String,
    val item: String,
    val title: String,
    val url: String,
    val thumbnail: String,
    val thumbnail_attribute: String,
    val url_attribute: String,
    val pagination: String,
    val rating: String? = null,
    val year: String? = null,
    val quality: String? = null,
    val duration: String? = null,
    val genre: String? = null
)

@Serializable
data class DetailSelector(
    val title: String,
    val poster_primary: String,
    val poster_fallback: String,
    val poster_attribute: String,
    val genre: String,
    val synopsis: String,
    val year: String,
    val rating: String,
    val duration: String,
    val quality: String,
    val country: String? = null,
    val episode_indicator: String,
    val episode_list: String,
    val player_list: String,
    val player_iframe: String
)

@Serializable
data class PlayerSelector(
    val server_name: String,
    val data_server: String,
    val data_url: String
)

@Serializable
data class PosterMatchingConfig(
    val enabled: Boolean,
    val validation: PosterValidation
)

@Serializable
data class PosterValidation(
    val check_slug_in_poster: Boolean,
    val check_title_in_poster: Boolean,
    val ignore_case: Boolean,
    val remove_special_chars: Boolean,
    val allowed_extensions: List<String>
)

@Serializable
data class DataNormalizationConfig(
    val title: TitleNormalization,
    val thumbnail: ThumbnailNormalization,
    val url: UrlNormalization
)

@Serializable
data class TitleNormalization(
    val trim: Boolean,
    val remove_year_suffix: Boolean,
    val decode_html_entities: Boolean
)

@Serializable
data class ThumbnailNormalization(
    val prefer_webp: Boolean,
    val prefer_high_quality: Boolean,
    val validate_url: Boolean
)

@Serializable
data class UrlNormalization(
    val ensure_absolute: Boolean,
    val trim_trailing_slash: Boolean
)

@Serializable
data class FilteringConfig(
    val duplicate_detection: DuplicateDetection,
    val exclude_series: ExcludeSeries
)

@Serializable
data class DuplicateDetection(
    val enabled: Boolean,
    val method: String,
    val case_sensitive: Boolean,
    val trim_whitespace: Boolean
)

@Serializable
data class ExcludeSeries(
    val enabled_for_movies: Boolean,
    val indicator: String
)

/**
 * Helper class to parse and apply scraper configuration
 */
class ScraperConfigHelper(private val config: ScraperConfig) {

    companion object {
        private val json = Json { 
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun fromJson(jsonString: String): ScraperConfigHelper {
            val config = json.decodeFromString<ScraperConfig>(jsonString)
            return ScraperConfigHelper(config)
        }
    }

    /**
     * Build URL for a given section and page
     */
    fun buildUrl(baseUrl: String, section: String, page: Int, query: String = ""): String {
        val endpoint = when (section) {
            "popular" -> config.endpoints.popular
            "latest" -> config.endpoints.latest
            "search" -> config.endpoints.search
            else -> config.endpoints.popular
        }

        return if (page == 1) {
            "$baseUrl${endpoint.path}".replace("{query}", query)
        } else {
            "$baseUrl${endpoint.page_format}"
                .replace("{page}", page.toString())
                .replace("{query}", query)
        }
    }

    /**
     * Get selector config for a section
     */
    fun getSelectorConfig(section: String): SectionSelector {
        return when (section) {
            "popular" -> config.selectors.popular
            "latest" -> config.selectors.latest
            "search" -> config.selectors.search
            else -> config.selectors.popular
        }
    }

    /**
     * Parse item from element using selector config
     */
    fun parseItemFromElement(element: Element, selector: SectionSelector, baseUrl: String): ParsedItem? {
        return try {
            val title = element.selectFirst(selector.title)?.text()?.trim() ?: return null
            val urlElement = element.selectFirst(selector.url) ?: return null
            val url = urlElement.attr(selector.url_attribute)
            
            if (url.isEmpty() || title.isEmpty()) return null

            // Get thumbnail with validation
            val thumbnailUrl = element.selectFirst(selector.thumbnail)
                ?.attr(selector.thumbnail_attribute) ?: ""

            ParsedItem(
                title = normalizeTitle(title),
                url = normalizeUrl(url, baseUrl),
                thumbnail = thumbnailUrl,
                rating = element.selectFirst(selector.rating)?.text(),
                year = element.selectFirst(selector.year)?.text(),
                quality = element.selectFirst(selector.quality)?.text(),
                duration = selector.duration?.let { element.selectFirst(it)?.text() },
                genre = selector.genre?.let { element.selectFirst(it)?.text() }
            )
        } catch (e: Exception) {
            ReportLog.reportError("ScraperConfig", "Failed to parse item: ${e.message}")
            null
        }
    }

    /**
     * Smart poster matching to avoid mismatch
     */
    fun matchPoster(document: Document, pageUrl: String, filmTitle: String): String {
        if (!config.poster_matching.enabled) {
            // Fallback to simple selection
            return document.selectFirst(config.selectors.detail.poster_primary)
                ?.attr(config.selectors.detail.poster_attribute) ?: ""
        }

        val validation = config.poster_matching.validation

        // Extract slug from URL (e.g., /night-gaua-2025/ → night-gaua-2025)
        val slug = extractSlug(pageUrl)
        val normalizedTitle = normalizeForMatching(filmTitle, validation)

        ReportLog.log("PosterMatch", "Matching poster for: $filmTitle (slug: $slug)", LogLevel.DEBUG)

        // Get all possible poster images
        val allImages = document.select("${config.selectors.detail.poster_primary}, ${config.selectors.detail.poster_fallback}")

        // Strategy 1: Slug match (highest priority)
        if (validation.check_slug_in_poster && slug.isNotEmpty()) {
            val slugMatch = allImages.firstOrNull { img ->
                val src = img.attr(config.selectors.detail.poster_attribute)
                containsMatch(src, slug, validation.ignore_case)
            }
            if (slugMatch != null) {
                val posterUrl = slugMatch.attr(config.selectors.detail.poster_attribute)
                ReportLog.log("PosterMatch", "✓ Slug match found: $posterUrl", LogLevel.INFO)
                return posterUrl
            }
        }

        // Strategy 2: Title match
        if (validation.check_title_in_poster && normalizedTitle.isNotEmpty()) {
            val titleMatch = allImages.firstOrNull { img ->
                val src = img.attr(config.selectors.detail.poster_attribute)
                containsMatch(src, normalizedTitle, validation.ignore_case)
            }
            if (titleMatch != null) {
                val posterUrl = titleMatch.attr(config.selectors.detail.poster_attribute)
                ReportLog.log("PosterMatch", "✓ Title match found: $posterUrl", LogLevel.INFO)
                return posterUrl
            }
        }

        // Strategy 3: Fallback to first valid poster
        val fallbackPoster = allImages.firstOrNull()
            ?.attr(config.selectors.detail.poster_attribute) ?: ""
        
        if (fallbackPoster.isNotEmpty()) {
            ReportLog.log("PosterMatch", "⚠ Using fallback poster: $fallbackPoster", LogLevel.WARN)
        } else {
            ReportLog.log("PosterMatch", "✗ No poster found", LogLevel.ERROR)
        }

        return fallbackPoster
    }

    /**
     * Extract slug from URL
     */
    private fun extractSlug(url: String): String {
        return try {
            url.trimEnd('/').substringAfterLast('/')
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Normalize string for matching
     */
    private fun normalizeForMatching(text: String, validation: PosterValidation): String {
        var normalized = text
        
        if (validation.remove_special_chars) {
            // Remove special chars but keep alphanumeric and hyphens
            normalized = normalized.replace(Regex("[^a-zA-Z0-9\\s-]"), "")
                .replace(Regex("\\s+"), "-")
        }
        
        if (validation.ignore_case) {
            normalized = normalized.lowercase()
        }
        
        return normalized.trim()
    }

    /**
     * Check if source contains match
     */
    private fun containsMatch(source: String, match: String, ignoreCase: Boolean): Boolean {
        return if (ignoreCase) {
            source.lowercase().contains(match.lowercase())
        } else {
            source.contains(match)
        }
    }

    /**
     * Normalize title according to config
     */
    private fun normalizeTitle(title: String): String {
        var normalized = title
        
        if (config.data_normalization.title.trim) {
            normalized = normalized.trim()
        }
        
        if (config.data_normalization.title.decode_html_entities) {
            normalized = normalized
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }
        
        return normalized
    }

    /**
     * Normalize URL according to config
     */
    private fun normalizeUrl(url: String, baseUrl: String): String {
        var normalized = url
        
        if (config.data_normalization.url.ensure_absolute && !url.startsWith("http")) {
            normalized = baseUrl + url
        }
        
        if (config.data_normalization.url.trim_trailing_slash) {
            normalized = normalized.trimEnd('/')
        }
        
        return normalized
    }

    /**
     * Check if element should be excluded (e.g., series when looking for movies)
     */
    fun shouldExclude(element: Element, forMovies: Boolean): Boolean {
        if (!forMovies) return false
        
        val excludeSeries = config.filtering.exclude_series
        if (!excludeSeries.enabled_for_movies) return false
        
        return element.selectFirst(excludeSeries.indicator) != null
    }
}

/**
 * Parsed item data class
 */
data class ParsedItem(
    val title: String,
    val url: String,
    val thumbnail: String,
    val rating: String? = null,
    val year: String? = null,
    val quality: String? = null,
    val duration: String? = null,
    val genre: String? = null
)
