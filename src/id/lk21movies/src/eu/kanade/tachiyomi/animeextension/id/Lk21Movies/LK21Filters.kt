package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.content.SharedPreferences
import android.util.Log
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Filter System untuk LK21Movies
 * Dengan cache support untuk live scraping
 */
object LK21Filters {

    private const val TAG = "LK21Filters"

    // Cache Keys
    private const val CACHE_GENRES_KEY = "cached_genres"
    private const val CACHE_COUNTRIES_KEY = "cached_countries"
    private const val CACHE_YEARS_KEY = "cached_years"
    private const val CACHE_TIMESTAMP_KEY = "filter_cache_timestamp"

    // Cache duration: 24 hours
    private const val CACHE_DURATION = 24 * 60 * 60 * 1000L

    // Default values jika scraping gagal
    private val DEFAULT_GENRES = arrayOf(
        "Semua Genre", "Action", "Adventure", "Animation", "Biography",
        "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy",
        "History", "Horror", "Music", "Mystery", "Romance", "Sci-Fi",
        "Sport", "Thriller", "War", "Western",
    )

    private val DEFAULT_COUNTRIES = arrayOf(
        "Semua Negara", "USA", "Indonesia", "Korea", "Japan", "China",
        "India", "Thailand", "United Kingdom", "France", "Germany",
    )

    private val DEFAULT_YEARS = arrayOf(
        "Semua Tahun", "2025", "2024", "2023", "2022", "2021",
        "2020", "2019", "2018", "2017", "2016", "2015",
    )

    // Runtime variables
    var genres = DEFAULT_GENRES
        private set
    var countries = DEFAULT_COUNTRIES
        private set
    var years = DEFAULT_YEARS
        private set

    /**
     * Initialize filters dengan cache atau live scraping
     */
    fun initialize(
        client: OkHttpClient,
        baseUrl: String,
        preferences: SharedPreferences,
    ) {
        val lastUpdate = preferences.getLong(CACHE_TIMESTAMP_KEY, 0L)
        val currentTime = System.currentTimeMillis()

        // Coba load dari cache dulu
        if (currentTime - lastUpdate < CACHE_DURATION) {
            loadFromCache(preferences)
            if (genres.size > 1) {
                Log.d(TAG, "Loaded filters from cache")
                return
            }
        }

        // Jika cache expired atau kosong, scrape dari website
        scrapeFilters(client, baseUrl, preferences)
    }

    /**
     * Load filters dari cache
     */
    private fun loadFromCache(preferences: SharedPreferences) {
        val cachedGenres = preferences.getString(CACHE_GENRES_KEY, null)
        val cachedCountries = preferences.getString(CACHE_COUNTRIES_KEY, null)
        val cachedYears = preferences.getString(CACHE_YEARS_KEY, null)

        if (cachedGenres != null) {
            genres = cachedGenres.split("|").toTypedArray()
        }
        if (cachedCountries != null) {
            countries = cachedCountries.split("|").toTypedArray()
        }
        if (cachedYears != null) {
            years = cachedYears.split("|").toTypedArray()
        }
    }

    /**
     * Scrape filters dari homepage dan save ke cache
     */
    private fun scrapeFilters(
        client: OkHttpClient,
        baseUrl: String,
        preferences: SharedPreferences,
    ) {
        try {
            Log.d(TAG, "Scraping filters from: $baseUrl")

            val request = Request.Builder()
                .url(baseUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to scrape filters: ${response.code}")
                useDefaults()
                return
            }

            val html = response.body?.string() ?: run {
                useDefaults()
                return
            }

            val document = Jsoup.parse(html)

            // Scrape Genres
            val genresList = mutableListOf("Semua Genre")
            document.select("ul.genre-list li a, .genre-filter a, a[href*='/genre/']").forEach {
                val genre = it.text().trim()
                if (genre.isNotBlank() && !genresList.contains(genre)) {
                    genresList.add(genre)
                }
            }

            // Scrape Countries
            val countriesList = mutableListOf("Semua Negara")
            document.select("ul.country-list li a, .country-filter a, a[href*='/country/']").forEach {
                val country = it.text().trim()
                if (country.isNotBlank() && !countriesList.contains(country)) {
                    countriesList.add(country)
                }
            }

            // Scrape Years
            val yearsList = mutableListOf("Semua Tahun")
            document.select("ul.year-list li a, .year-filter a, a[href*='/year/']").forEach {
                val year = it.text().trim()
                if (year.matches(Regex("\\d{4}")) && !yearsList.contains(year)) {
                    yearsList.add(year)
                }
            }

            // Update jika scraping berhasil
            if (genresList.size > 1) {
                genres = genresList.toTypedArray()
                saveToCache(CACHE_GENRES_KEY, genres, preferences)
            }

            if (countriesList.size > 1) {
                countries = countriesList.toTypedArray()
                saveToCache(CACHE_COUNTRIES_KEY, countries, preferences)
            }

            if (yearsList.size > 1) {
                years = yearsList.toTypedArray()
                saveToCache(CACHE_YEARS_KEY, years, preferences)
            }

            // Update timestamp
            preferences.edit()
                .putLong(CACHE_TIMESTAMP_KEY, System.currentTimeMillis())
                .apply()

            Log.d(TAG, "Scraped ${genres.size} genres, ${countries.size} countries, ${years.size} years")
        } catch (e: Exception) {
            Log.e(TAG, "Error scraping filters", e)
            useDefaults()
        }
    }

    /**
     * Save array to cache
     */
    private fun saveToCache(key: String, data: Array<String>, preferences: SharedPreferences) {
        preferences.edit()
            .putString(key, data.joinToString("|"))
            .apply()
    }

    /**
     * Fallback ke default values
     */
    private fun useDefaults() {
        genres = DEFAULT_GENRES
        countries = DEFAULT_COUNTRIES
        years = DEFAULT_YEARS
    }

    /**
     * Clear cache (untuk force refresh)
     */
    fun clearCache(preferences: SharedPreferences) {
        preferences.edit()
            .remove(CACHE_GENRES_KEY)
            .remove(CACHE_COUNTRIES_KEY)
            .remove(CACHE_YEARS_KEY)
            .remove(CACHE_TIMESTAMP_KEY)
            .apply()
    }
}

// Filter Classes
class GenreFilter(name: String) : AnimeFilter.Select<String>(name, LK21Filters.genres)
class YearFilter : AnimeFilter.Select<String>("Tahun", LK21Filters.years)
class CountryFilter : AnimeFilter.Select<String>("Negara", LK21Filters.countries)

class SortFilter : AnimeFilter.Select<String>(
    "Urutkan",
    arrayOf(
        "Default",
        "Rating Tertinggi",
        "Paling Populer",
        "IMDB 9+",
        "IMDB 8+",
        "IMDB 7+",
    ),
)
