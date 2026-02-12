package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.content.SharedPreferences

/**
 * Configuration Manager untuk LK21Movies
 * Simple config tanpa GitHub self-healing
 */
object LK21Config {

    // Preference Keys
    const val PREF_BASE_URL_KEY = "base_url"
    const val PREF_QUALITY_KEY = "preferred_quality"

    // Defaults
    const val DEFAULT_BASE_URL = "https://tv8.lk21official.cc/"
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

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
}
