package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen

/**
 * Preferences Screen untuk LK21Movies
 * FINAL FIX: No isEnabled, safe intent handling untuk Tachiyomi
 */
object LK21Preferences {

    fun setupPreferenceScreen(
        screen: PreferenceScreen,
        preferences: SharedPreferences,
    ) {
        val context = screen.context

        // ================ 1. API GITHUB URL ================
        EditTextPreference(context).apply {
            key = LK21Config.PREF_API_URL_KEY
            title = "API Configuration URL"
            setDefaultValue(LK21Config.DEFAULT_API_URL)
            summary = preferences.getString(LK21Config.PREF_API_URL_KEY, LK21Config.DEFAULT_API_URL)?.trim() ?: ""
            dialogTitle = "GitHub Raw JSON Link"
            dialogMessage = "Link ke file extension_lk21movies.json di GitHub untuk self-healing"
            setOnPreferenceChangeListener { _, newValue ->
                summary = (newValue as String).trim()
                true
            }
        }.also(screen::addPreference)

        // ================ 2. BASE URL OVERRIDE ================
        EditTextPreference(context).apply {
            key = LK21Config.PREF_BASE_URL_KEY
            title = "Base URL (Manual Override)"
            setDefaultValue(LK21Config.DEFAULT_BASE_URL)
            summary = preferences.getString(LK21Config.PREF_BASE_URL_KEY, LK21Config.DEFAULT_BASE_URL) ?: ""
            dialogTitle = "Domain LK21 Aktif"
            dialogMessage = "Kosongkan untuk menggunakan self-healing otomatis"
            setOnPreferenceChangeListener { _, newValue ->
                summary = newValue as String
                true
            }
        }.also(screen::addPreference)

        // ================ 3. PREFERRED QUALITY ================
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

        // ================ 4. CLEAR FILTER CACHE ================
        Preference().apply {
            key = "clear_filter_cache"
            title = "Hapus Cache Filter"
            summary = "Refresh daftar Genre, Negara, dan Tahun (tap untuk reset)"
            setOnPreferenceClickListener {
                LK21Filters.clearCache(preferences)
                preferences.edit().putLong(LK21Config.PREF_LAST_UPDATE_KEY, 0L).apply()
                summary = "Cache berhasil dihapus! Restart extension untuk refresh."
                true
            }
        }.also(screen::addPreference)

        // ================ 5. EXTENSION VERSION ================
        Preference().apply {
            key = "extension_version"
            title = "Versi Extension"
            summary = "LK21Movies v2.0 - Build Success"
            // REMOVED: isEnabled tidak supported di extension Preference()
        }.also(screen::addPreference)

        // ================ 6. GITHUB REPOSITORY ================
        Preference().apply {
            key = "github_repo"
            title = "GitHub Repository"
            summary = "https://github.com/Usermongkay/Usermongkay"
            setOnPreferenceClickListener {
                // SIMPLIFIED: Intent tidak reliable di extension, pakai URL di summary
                true
            }
        }.also(screen::addPreference)

        // ================ 7. FITUR INFO ================
        Preference().apply {
            key = "feature_info"
            title = "Fitur Extension"
            summary = "Self-healing domain, Live filter, YouTube trailer, Quality selector, Filter cache"
            // REMOVED: isEnabled tidak supported
        }.also(screen::addPreference)
    }
}
