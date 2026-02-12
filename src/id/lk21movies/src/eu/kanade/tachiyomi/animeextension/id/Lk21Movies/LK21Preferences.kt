package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen

/**
 * Preferences Screen untuk LK21Movies
 * Clean version tanpa GitHub API
 */
object LK21Preferences {

    fun setupPreferenceScreen(
        screen: PreferenceScreen,
        preferences: SharedPreferences,
    ) {
        val context = screen.context

        // ================ 1. BASE URL ================
        EditTextPreference(context).apply {
            key = LK21Config.PREF_BASE_URL_KEY
            title = "Base URL"
            setDefaultValue(LK21Config.DEFAULT_BASE_URL)
            summary = preferences.getString(LK21Config.PREF_BASE_URL_KEY, LK21Config.DEFAULT_BASE_URL) ?: ""
            dialogTitle = "Domain LK21"
            dialogMessage = "Masukkan base URL aktif untuk LK21Movies"
            setOnPreferenceChangeListener { _, newValue ->
                summary = newValue as String
                true
            }
        }.also(screen::addPreference)

        // ================ 2. PREFERRED QUALITY ================
        ListPreference(context).apply {
            key = LK21Config.PREF_QUALITY_KEY
            title = "Kualitas Prioritas"
            entries = arrayOf(
                "1080p (Full HD)",
                "720p (HD)",
                "480p (SD)",
                "360p (Low)",
                "Auto (Otomatis)",
            )
            entryValues = arrayOf("1080", "720", "480", "360", "auto")
            setDefaultValue("720")

            val currentQuality = preferences.getString(LK21Config.PREF_QUALITY_KEY, "720") ?: "720"
            val qualityLabel = when (currentQuality) {
                "1080" -> "1080p (Full HD)"
                "720" -> "720p (HD)"
                "480" -> "480p (SD)"
                "360" -> "360p (Low)"
                else -> "Auto (Otomatis)"
            }
            summary = "Video dengan kualitas $qualityLabel akan diprioritaskan"

            setOnPreferenceChangeListener { preference, newValue ->
                val quality = when (newValue) {
                    "1080" -> "1080p (Full HD)"
                    "720" -> "720p (HD)"
                    "480" -> "480p (SD)"
                    "360" -> "360p (Low)"
                    else -> "Auto (Otomatis)"
                }
                preference.summary = "Video dengan kualitas $quality akan diprioritaskan"
                true
            }
        }.also(screen::addPreference)

        // ================ 3. CLEAR FILTER CACHE ================
        Preference().apply {
            key = "clear_filter_cache"
            title = "Hapus Cache Filter"
            summary = "Refresh daftar Genre, Negara, dan Tahun (tap untuk reset)"
            setOnPreferenceClickListener {
                LK21Filters.clearCache(preferences)
                summary = "Cache berhasil dihapus! Restart extension untuk refresh."
                true
            }
        }.also(screen::addPreference)

        // ================ 4. EXTENSION VERSION ================
        Preference().apply {
            key = "extension_version"
            title = "Versi Extension"
            summary = "LK21Movies - Clean Build (No GitHub API)"
        }.also(screen::addPreference)

        // ================ 5. FITUR INFO ================
        Preference().apply {
            key = "feature_info"
            title = "Fitur Extension"
            summary = "Live filter scraping, YouTube trailer, Quality selector, Filter cache"
        }.also(screen::addPreference)
    }
}
