package eu.kanade.tachiyomi.animeextension.id.lk21movies

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Parser untuk HTML scraping LK21Movies
 * Separated dari main class untuk better organization
 */
object LK21Parser {

    // === SELECTORS ===
    const val POPULAR_SELECTOR = ".widget li.slider"
    const val NEXT_PAGE_SELECTOR = "a.next"

    // Detail selectors
    private const val TITLE_SELECTOR = "article figure a[title]"
    private const val THUMBNAIL_SELECTOR = "picture img"
    private const val EPISODE_BADGE_SELECTOR = ".episode"

    // Detail page selectors
    private const val DESCRIPTION_SELECTOR = "div.meta-info div.synopsis.expanded, .sinopsis p"
    private const val GENRE_SELECTOR = "div.tag-list span.tag a[href*='/genre/'], .genre a"
    private const val DIRECTOR_SELECTOR = "div.detail p a[href*='/director/'], .director a"
    private const val CAST_SELECTOR = "div.detail p a[href*='/artist/'], .cast a"

    // Episode/Video selectors
    const val MAIN_IFRAME_SELECTOR = "iframe#main-player, iframe.player-iframe"
    const val PLAYER_LIST_SELECTOR = "ul#player-list li a"
    const val TRAILER_SELECTOR = "a.yt-lightbox, a[href*='youtube.com/watch']"

    /**
     * Parse anime item dari element (untuk Popular/Latest/Search)
     * @param baseUrl untuk normalisasi relative URL
     * @return SAnime or null jika element invalid
     */
    fun parseAnimeFromElement(element: Element, baseUrl: String): SAnime? {
        // Filter out Drama/Series (hanya Movie)
        if (element.select(EPISODE_BADGE_SELECTOR).isNotEmpty()) {
            return null
        }

        return try {
            SAnime.create().apply {
                val linkElement = element.select(TITLE_SELECTOR).first() ?: return null

                title = linkElement.attr("title").ifBlank {
                    linkElement.text()
                }

                // Normalize URL
                url = linkElement.attr("href").let { href ->
                    when {
                        href.startsWith("http") -> {
                            // Absolute URL, extract path only
                            href.substringAfter(baseUrl).removePrefix("/")
                        }
                        href.startsWith("/") -> {
                            // Relative URL dengan leading slash
                            href.removePrefix("/")
                        }
                        else -> {
                            // Already relative
                            href
                        }
                    }
                }

                thumbnail_url = element.select(THUMBNAIL_SELECTOR).attr("src").let { src ->
                    when {
                        src.startsWith("data:") -> element.select(THUMBNAIL_SELECTOR).attr("data-src")
                        src.isBlank() -> element.select(THUMBNAIL_SELECTOR).attr("data-lazy-src")
                        else -> src
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse anime details dari document
     */
    fun parseAnimeDetails(document: Document): SAnime {
        return SAnime.create().apply {
            // Description/Synopsis
            description = document.select(DESCRIPTION_SELECTOR).text().trim().let {
                if (it.isBlank()) "Tidak ada sinopsis tersedia" else it
            }

            // Genre
            genre = document.select(GENRE_SELECTOR).joinToString(", ") {
                it.text().trim()
            }.ifBlank { "Unknown" }

            // Director
            author = document.select(DIRECTOR_SELECTOR).first()?.text()?.trim()

            // Cast
            artist = document.select(CAST_SELECTOR).joinToString(", ") {
                it.text().trim()
            }

            // Status (Movies are completed)
            status = SAnime.COMPLETED
        }
    }

    /**
     * Parse episode list
     * Episode 1 = Film Utama
     * Episode 2 = Trailer (jika ada)
     */
    fun parseEpisodeList(document: Document, url: String): List<SEpisode> {
        val episodes = mutableListOf<SEpisode>()

        // Episode 1: Full Movie
        episodes.add(
            SEpisode.create().apply {
                name = "Film Utama"
                episode_number = 1f
                this.url = url
                date_upload = System.currentTimeMillis()
            },
        )

        // Episode 2: Trailer (jika ada YouTube link)
        val trailerLink = document.select(TRAILER_SELECTOR).attr("href")
        if (trailerLink.isNotBlank() && trailerLink.contains("youtube.com")) {
            episodes.add(
                SEpisode.create().apply {
                    name = "Trailer"
                    episode_number = 2f
                    this.url = trailerLink
                    date_upload = System.currentTimeMillis()
                },
            )
        }

        return episodes
    }

    /**
     * Check if page has next page
     */
    fun hasNextPage(document: Document): Boolean {
        return document.select(NEXT_PAGE_SELECTOR).isNotEmpty()
    }

    /**
     * Build filter URL berdasarkan filter yang dipilih
     */
    fun buildFilterUrl(
        baseUrl: String,
        page: Int,
        genreState: Int,
        yearState: Int,
        countryState: Int,
        sortState: Int,
    ): String {
        val cleanBaseUrl = baseUrl.removeSuffix("/")
        var url = "$cleanBaseUrl/page/$page"

        // Priority: Genre > Year > Country
        when {
            genreState > 0 -> {
                val genre = LK21Filters.genres[genreState]
                    .lowercase()
                    .replace(" ", "-")
                url = "$cleanBaseUrl/genre/$genre/page/$page"
            }
            yearState > 0 -> {
                val year = LK21Filters.years[yearState]
                url = "$cleanBaseUrl/year/$year/page/$page"
            }
            countryState > 0 -> {
                val country = LK21Filters.countries[countryState]
                    .lowercase()
                    .replace(" ", "-")
                url = "$cleanBaseUrl/country/$country/page/$page"
            }
        }

        // Apply sort filter
        when (sortState) {
            1 -> url = "$cleanBaseUrl/rating/page/$page"
            2 -> url = "$cleanBaseUrl/most-viewed/page/$page"
            3 -> url = "$cleanBaseUrl/imdb-rating-9/page/$page"
            4 -> url = "$cleanBaseUrl/imdb-rating-8/page/$page"
            5 -> url = "$cleanBaseUrl/imdb-rating-7/page/$page"
        }

        return url
    }
}
