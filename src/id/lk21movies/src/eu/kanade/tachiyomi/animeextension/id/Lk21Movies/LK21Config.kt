package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.content.SharedPreferences
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Configuration Manager untuk LK21Movies
 * Handles self-healing domain via GitHub JSON
 */
object LK21Config {

    private const val TAG = "LK21Config"

    // Preference Keys
    const val PREF_API_URL_KEY = "api_github_url"
    const val PREF_BASE_URL_KEY = "base_url"
    const val PREF_QUALITY_KEY = "preferred_quality"
    const val PREF_LAST_UPDATE_KEY = "last_config_update"

    // Defaults
    const val DEFAULT_API_URL = "https://raw.githubusercontent.com/Usermongkay/Usermongkay/refs/heads/main/lk21movies/extension_lk21movies.json"
    const val DEFAULT_BASE_URL = "https://tv8.lk21official.cc/"
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    // Cache duration: 6 hours
    private const val CONFIG_CACHE_DURATION = 6 * 60 * 60 * 1000L

    /**
     * Fetch configuration dari GitHub dan update base URL jika perlu
     * @return Updated base URL
     */
    fun fetchAndUpdateConfig(
        client: OkHttpClient,
        preferences: SharedPreferences
    ): String {
        val lastUpdate = preferences.getLong(PREF_LAST_UPDATE_KEY, 0L)
        val currentTime = System.currentTimeMillis()

        // Jika cache masih valid, return current base URL
        if (currentTime - lastUpdate < CONFIG_CACHE_DURATION) {
            return getBaseUrl(preferences)
        }

        val apiUrl = getApiUrl(preferences)

        return try {
            Log.d(TAG, "Fetching config from: $apiUrl")

            val request = Request.Builder()
                .url(apiUrl)
                .get()
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to fetch config: ${response.code}")
                return getBaseUrl(preferences)
            }

            val jsonString = response.body?.string() ?: return getBaseUrl(preferences)
            val jsonObject = JSONObject(jsonString)

            // Parse network config
            val networkConfig = jsonObject.optJSONObject("network")
            val newBaseUrl = networkConfig?.optString("default_baseUrl")

            if (!newBaseUrl.isNullOrBlank() && newBaseUrl.startsWith("http")) {
                Log.d(TAG, "Updating base URL to: $newBaseUrl")

                // Update preferences
                preferences.edit()
                    .putString(PREF_BASE_URL_KEY, newBaseUrl)
                    .putLong(PREF_LAST_UPDATE_KEY, currentTime)
                    .apply()

                newBaseUrl
            } else {
                Log.w(TAG, "Invalid base URL in config")
                getBaseUrl(preferences)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching config", e)
            getBaseUrl(preferences)
        }
    }

    /**
     * Get API URL from preferences
     */
    fun getApiUrl(preferences: SharedPreferences): String {
        return preferences.getString(PREF_API_URL_KEY, DEFAULT_API_URL) ?: DEFAULT_API_URL
    }

    /**
     * Get Base URL from preferences
     */
    fun getBaseUrl(preferences: SharedPreferences): String {
        return preferences.getString(PREF_BASE_URL_KEY, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    /**
     * Get preferred quality
     */
    fun getPreferredQuality(preferences: SharedPreferences): String {
        return preferences.getString(PREF_QUALITY_KEY, "720") ?: "720"
    }

    /**
     * Force update configuration (untuk testing atau manual refresh)
     */
    fun forceUpdateConfig(
        client: OkHttpClient,
        preferences: SharedPreferences
    ): String {
        // Reset last update time
        preferences.edit()
            .putLong(PREF_LAST_UPDATE_KEY, 0L)
            .apply()

        return fetchAndUpdateConfig(client, preferences)
    }
}
