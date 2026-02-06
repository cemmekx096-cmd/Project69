package eu.kanade.tachiyomi.lib.lk21extractor

import android.content.SharedPreferences
import android.util.Log
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

/**
 * Dynamic filter system untuk LK21
 * Auto-fetch genres, years, dan countries dari website
 */
object Lk21Filters {
    
    private const val TAG = "Lk21Filters"
    
    // Cache keys
    private const val CACHE_GENRES_KEY = "cached_genres"
    private const val CACHE_COUNTRIES_KEY = "cached_countries"
    private const val CACHE_TIMESTAMP_KEY = "cache_timestamp"
    
    // Cache duration: 7 days
    private const val CACHE_DURATION_MS = 7L * 24 * 60 * 60 * 1000
    
    /**
     * Get filter list dengan dynamic data
     */
    fun getFilterList(
        client: OkHttpClient,
        baseUrl: String,
        preferences: SharedPreferences,
    ): AnimeFilterList {
        // Check if cache is expired
        val cacheTimestamp = preferences.getLong(CACHE_TIMESTAMP_KEY, 0)
        val isCacheExpired = System.currentTimeMillis() - cacheTimestamp > CACHE_DURATION_MS
        
        // Load genres
        val genres = if (isCacheExpired) {
            fetchAndCacheGenres(client, baseUrl, preferences)
        } else {
            loadCachedGenres(preferences)
        }
        
        // Load countries
        val countries = if (isCacheExpired) {
            fetchAndCacheCountries(client, baseUrl, preferences)
        } else {
            loadCachedCountries(preferences)
        }
        
        // Generate years (2026 down to 2000)
        val years = (2026 downTo 2000).map { it.toString() }
        
        return AnimeFilterList(
            AnimeFilter.Header("🎬 ${genres.size} genres, ${countries.size} countries"),
            GenreFilter(genres),
            YearFilter(years),
            CountryFilter(countries),
        )
    }
    
    /**
     * Fetch genres from website and cache
     */
    private fun fetchAndCacheGenres(
        client: OkHttpClient,
        baseUrl: String,
        preferences: SharedPreferences,
    ): List<Pair<String, String>> {
        return try {
            Log.d(TAG, "Fetching genres from: $baseUrl/genre/")
            
            val response = client.newCall(
                okhttp3.Request.Builder()
                    .url("$baseUrl/genre/")
                    .header("User-Agent", Lk21Common.USER_AGENT)
                    .build(),
            ).execute()
            
            val doc = Jsoup.parse(response.body.string())
            
            val genres = doc.select("a[href*='/genre/']")
                .mapNotNull { element ->
                    val name = element.text().trim()
                    val href = element.attr("href")
                    val slug = href.substringAfter("/genre/").substringBefore("/")
                    
                    if (name.isNotEmpty() && slug.isNotEmpty()) {
                        Pair(name, slug)
                    } else {
                        null
                    }
                }
                .distinctBy { it.second }
                .sortedBy { it.first }
            
            Log.d(TAG, "Found ${genres.size} genres")
            
            // Cache to preferences
            val serialized = genres.joinToString("|") { "${it.first}:${it.second}" }
            preferences.edit()
                .putString(CACHE_GENRES_KEY, serialized)
                .putLong(CACHE_TIMESTAMP_KEY, System.currentTimeMillis())
                .apply()
            
            response.close()
            
            genres
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching genres: ${e.message}")
            getDefaultGenres()
        }
    }
    
    /**
     * Load cached genres from preferences
     */
    private fun loadCachedGenres(preferences: SharedPreferences): List<Pair<String, String>> {
        val cached = preferences.getString(CACHE_GENRES_KEY, null)
        
        return if (cached != null) {
            cached.split("|").mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) Pair(parts[0], parts[1]) else null
            }
        } else {
            getDefaultGenres()
        }
    }
    
    /**
     * Fetch countries from website and cache
     */
    private fun fetchAndCacheCountries(
        client: OkHttpClient,
        baseUrl: String,
        preferences: SharedPreferences,
    ): List<Pair<String, String>> {
        return try {
            Log.d(TAG, "Fetching countries from: $baseUrl/country/")
            
            val response = client.newCall(
                okhttp3.Request.Builder()
                    .url("$baseUrl/country/")
                    .header("User-Agent", Lk21Common.USER_AGENT)
                    .build(),
            ).execute()
            
            val doc = Jsoup.parse(response.body.string())
            
            val countries = doc.select("a[href*='/country/']")
                .mapNotNull { element ->
                    val name = element.text().trim()
                    val href = element.attr("href")
                    val slug = href.substringAfter("/country/").substringBefore("/")
                    
                    if (name.isNotEmpty() && slug.isNotEmpty()) {
                        Pair(name, slug)
                    } else {
                        null
                    }
                }
                .distinctBy { it.second }
                .sortedBy { it.first }
            
            Log.d(TAG, "Found ${countries.size} countries")
            
            // Cache to preferences
            val serialized = countries.joinToString("|") { "${it.first}:${it.second}" }
            preferences.edit()
                .putString(CACHE_COUNTRIES_KEY, serialized)
                .apply()
            
            response.close()
            
            countries
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching countries: ${e.message}")
            getDefaultCountries()
        }
    }
    
    /**
     * Load cached countries from preferences
     */
    private fun loadCachedCountries(preferences: SharedPreferences): List<Pair<String, String>> {
        val cached = preferences.getString(CACHE_COUNTRIES_KEY, null)
        
        return if (cached != null) {
            cached.split("|").mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) Pair(parts[0], parts[1]) else null
            }
        } else {
            getDefaultCountries()
        }
    }
    
    /**
     * Default genres (fallback)
     */
    private fun getDefaultGenres(): List<Pair<String, String>> {
        return listOf(
            Pair("Action", "action"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Horror", "horror"),
            Pair("Romance", "romance"),
            Pair("Thriller", "thriller"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Adventure", "adventure"),
            Pair("Crime", "crime"),
            Pair("Fantasy", "fantasy"),
        )
    }
    
    /**
     * Default countries (fallback)
     */
    private fun getDefaultCountries(): List<Pair<String, String>> {
        return listOf(
            Pair("United States", "usa"),
            Pair("South Korea", "south-korea"),
            Pair("Indonesia", "indonesia"),
            Pair("Japan", "japan"),
            Pair("China", "china"),
            Pair("United Kingdom", "united-kingdom"),
            Pair("India", "india"),
            Pair("Thailand", "thailand"),
        )
    }
    
    /**
     * Clear cache (untuk manual refresh)
     */
    fun clearCache(preferences: SharedPreferences) {
        preferences.edit()
            .remove(CACHE_GENRES_KEY)
            .remove(CACHE_COUNTRIES_KEY)
            .remove(CACHE_TIMESTAMP_KEY)
            .apply()
        
        Log.d(TAG, "Cache cleared")
    }
}

// ========================== Filter Classes ==========================

class GenreFilter(private val genres: List<Pair<String, String>>) :
    AnimeFilter.Select<String>(
        "Genre",
        arrayOf("All") + genres.map { it.first }.toTypedArray(),
    ) {
    
    fun selected(): String? {
        return if (state == 0) null else genres[state - 1].second
    }
}

class YearFilter(private val years: List<String>) :
    AnimeFilter.Select<String>(
        "Year",
        arrayOf("All") + years.toTypedArray(),
    ) {
    
    fun selected(): String? {
        return if (state == 0) null else years[state - 1]
    }
}

class CountryFilter(private val countries: List<Pair<String, String>>) :
    AnimeFilter.Select<String>(
        "Country",
        arrayOf("All") + countries.map { it.first }.toTypedArray(),
    ) {
    
    fun selected(): String? {
        return if (state == 0) null else countries[state - 1].second
    }
}
