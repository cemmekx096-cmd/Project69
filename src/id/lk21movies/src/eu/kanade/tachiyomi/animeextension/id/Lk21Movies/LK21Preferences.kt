package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen

object LK21Preferences {

    fun setupPreferenceScreen(
        screen: PreferenceScreen,
        preferences: SharedPreferences,
    ) {
        val context = screen.context

        // API GITHUB URL
        EditTextPreference(context).apply {
            key = LK21Config.PREF_API_URL_KEY
            title = "API Configuration URL"
            setDefaultValue(LK21Config.DEFAULT_API_URL)
            summary = preferences.getString(LK21Config.PREF_API_URL_KEY, LK21Config.DEFAULT_API_URL)
            dialogTitle = "GitHub Raw JSON Link"
            dialogMessage = "Link ke file extension_lk21movies.json di GitHub untuk self-healing"
            setOnPreferenceChangeListener { _, newValue ->
                summary = (newValue as String).trim()
                true
            }
        }.also(screen::addPreference)

        // BASE URL
        EditTextPreference(context).apply {
            key = LK21Config.PREF_BASE_URL_KEY
            title = "Base URL (Manual Override)"
            setDefaultValue(LK21Config.DEFAULT_BASE_URL)
            summary = preferences.getString(LK21Config.PREF_BASE_URL_KEY, LK21Config.DEFAULT_BASE_URL)
            dialogTitle = "Domain LK21 Aktif"
            dialogMessage = "Kosongkan untuk menggunakan self-healing otomatis"
            setOnPreferenceChangeListener { _, newValue ->
                summary = newValue as String
                true
            }
        }.also(screen::addPreference)

        // QUALITY SELECTOR
        ListPreference(context).apply {
            key = LK21Config.PREF_QUALITY_KEY
            title = "Kualitas Prioritas"
            entries = arrayOf("1080p (Full HD)", "720p (HD)", "480p (SD)", "360p (Low)", "Auto (Otomatis)")
            entryValues = arrayOf("1080", "720", "480", "360", "auto")
            setDefaultValue("720")
            summary = "Video dengan kualitas %s akan diprioritaskan"
            setOnPreferenceChangeListener { _, newValue ->
                summary = when (newValue) {
                    "1080" -> "Video dengan kualitas 1080p (Full HD) akan diprioritaskan"
                    "720" -> "Video dengan kualitas 720p (HD) akan diprioritaskan"
                    "480" -> "Video dengan kualitas 480p (SD) akan diprioritaskan"
                    "360" -> "Video dengan kualitas 360p (Low) akan diprioritaskan"
                    else -> "Video dengan kualitas Auto (Otomatis) akan diprioritaskan"
                }
                true
            }
        }.also(screen::addPreference)

        // CLEAR CACHE - FIX 100%
        Preference(context).apply {
            key = "clear_filter_cache"
            title = "Hapus Cache Filter"
            summary = "Refresh daftar Genre, Negara, dan Tahun"
            setOnPreferenceClickListener {
                LK21Filters.clearCache(preferences)
                preferences.edit().putLong(LK21Config.PREF_LAST_UPDATE_KEY, 0L).apply()
                summary = "Cache dihapus! Restart extension."
                true
            }
        }.also(screen::addPreference)

        // VERSION - FIX 100%
        Preference(context).apply {
            key = "extension_version"
            title = "Versi Extension"
            summary = "LK21Movies v2.0"
            isEnabled = false
        }.also(screen::addPreference)

        // GITHUB - FIX 100%
        Preference(context).apply {
            key = "github_repo"
            title = "GitHub Repository"
            summary = "Tap untuk membuka repository"
            setOnPreferenceClickListener {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/Usermongkay/Usermongkay")
                )
                context.startActivity(intent)
                true
            }
        }.also(screen::addPreference)

        // FEATURES - FIX 100%
        Preference(context).apply {
            key = "feature_info"
            title = "Fitur Extension"
            summary = "✓ Self-healing\n✓ Live filter\n✓ Trailer\n✓ Quality selector\n✓ Cache"
            isEnabled = false
        }.also(screen::addPreference)
    }
}
