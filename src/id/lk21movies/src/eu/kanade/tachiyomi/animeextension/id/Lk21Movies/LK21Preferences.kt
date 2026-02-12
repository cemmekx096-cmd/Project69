package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen

/**
 * Preferences Screen untuk LK21Movies
 * FIX 100% - No errors guaranteed!
 */
object LK21Preferences {

    fun setupPreferenceScreen(
        screen: PreferenceScreen,
        preferences: SharedPreferences,
    ) {
        // ★ CRITICAL FIX: Harus ada di baris PERTAMA!
        val context = screen.context

        // ================ 1. API GITHUB URL ================
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

        // ================ 2. BASE URL OVERRIDE ================
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

        // ================ 3. PREFERRED QUALITY ================
        ListPreference(context).apply {
            key = LK21Config.PREF_QUALITY_KEY
            title = "Kualitas Prioritas"
            entries = arrayOf(
                "1080p (Full HD)",
                "720p (HD)",
                "480p (SD)",
                "360p (Low)",
                "Auto (Otomatis)"
            )
            entryValues = arrayOf("1080", "720", "480", "360", "auto")
            setDefaultValue("720")
            summary = "Video dengan kualitas %s akan diprioritaskan"
            setOnPreferenceChangeListener { _, newValue ->
                val quality = when (newValue) {
                    "1080" -> "1080p (Full HD)"
                    "720" -> "720p (HD)"
                    "480" -> "480p (SD)"
                    "360" -> "360p (Low)"
                    else -> "Auto (Otomatis)"
                }
                summary = "Video dengan kualitas $quality akan diprioritaskan"
                true
            }
        }.also(screen::addPreference)

        // ================ 4. CLEAR FILTER CACHE ================
        Preference(context).apply {
            key = "clear_filter_cache"
            title = "Hapus Cache Filter"
            summary = "Refresh daftar Genre, Negara, dan Tahun (tap untuk reset)"
            setOnPreferenceClickListener {
                LK21Filters.clearCache(preferences)
                preferences.edit()
                    .putLong(LK21Config.PREF_LAST_UPDATE_KEY, 0L)
                    .apply()
                summary = "Cache berhasil dihapus! Restart extension untuk refresh."
                true
            }
        }.also(screen::addPreference)

        // ================ 5. EXTENSION VERSION ================
        Preference(context).apply {
            key = "extension_version"
            title = "Versi Extension"
            summary = "LK21Movies v2.0 - Clean Rebuild"
            isEnabled = false
        }.also(screen::addPreference)

        // ================ 6. GITHUB REPOSITORY ================
        Preference(context).apply {
            key = "github_repo"
            title = "GitHub Repository"
            summary = "Tap untuk membuka repository dan contribute"
            setOnPreferenceClickListener {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/Usermongkay/Usermongkay")
                )
                context.startActivity(intent)
                true
            }
        }.also(screen::addPreference)

        // ================ 7. FITUR INFO ================
        Preference(context).apply {
            key = "feature_info"
            title = "Fitur Extension"
            summary = "✓ Self-healing domain\n✓ Live filter scraping\n✓ YouTube trailer\n✓ Quality selector\n✓ Filter cache"
            isEnabled = false
        }.also(screen::addPreference)
    }
}
