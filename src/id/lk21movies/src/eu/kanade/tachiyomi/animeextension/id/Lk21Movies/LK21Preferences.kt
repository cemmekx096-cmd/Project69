package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen

/**
 * Preferences Screen untuk LK21Movies
 * FIX: Semua error sudah diperbaiki!
 */
object LK21Preferences {

    fun setupPreferenceScreen(
        screen: PreferenceScreen,
        preferences: SharedPreferences,
    ) {
        // FIX: Variable context untuk menghindari Preference(screen.context)
        val context = screen.context

        // ================ API GITHUB URL ================
        EditTextPreference(context).apply {  // FIX: Pakai context, bukan screen.context
            key = LK21Config.PREF_API_URL_KEY
            title = "API Configuration URL"
            setDefaultValue(LK21Config.DEFAULT_API_URL)
            summary = preferences.getString(LK21Config.PREF_API_URL_KEY, LK21Config.DEFAULT_API_URL)
            dialogTitle = "GitHub Raw JSON Link"
            dialogMessage = "Link ke file extension_lk21movies.json di GitHub untuk self-healing"

            setOnPreferenceChangeListener { _, newValue ->
                val newUrl = (newValue as String).trim()
                summary = newUrl
                true
            }
        }.also(screen::addPreference)

        // ================ BASE URL OVERRIDE ================
        EditTextPreference(context).apply {  // FIX: Pakai context
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

        // ================ PREFERRED QUALITY ================
        ListPreference(context).apply {  // FIX: Pakai context
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

        // ================ CLEAR FILTER CACHE ================
        // FIX: Line ~75 (SEBELUMNYA ERROR LINE 80)
        Preference(context).apply {  // FIX: BUKAN Preference(screen.context)!
            key = "clear_filter_cache"  // FIX: Tambah key!
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

        // ================ EXTENSION VERSION ================
        // FIX: Line ~91 (SEBELUMNYA ERROR LINE 97 & 101)
        Preference(context).apply {  // FIX: BUKAN Preference(screen.context)!
            key = "extension_version"  // FIX: Tambah key!
            title = "Versi Extension"
            summary = "LK21Movies v2.0 - Clean Rebuild"
            isEnabled = false  // FIX: isEnabled valid karena di dalam apply block Preference(context)
        }.also(screen::addPreference)

        // ================ GITHUB REPOSITORY ================
        // FIX: Line ~100 (SEBELUMNYA ERROR LINE 105 & 115)
        Preference(context).apply {  // FIX: BUKAN Preference(screen.context)!
            key = "github_repo"  // FIX: Tambah key!
            title = "GitHub Repository"
            summary = "Tap untuk membuka repository dan contribute"

            setOnPreferenceClickListener {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/Usermongkay/Usermongkay"),
                )
                context.startActivity(intent)  // FIX: context dari variable, BUKAN screen.context!
                true
            }
        }.also(screen::addPreference)

        // ================ FITUR INFO ================
        // FIX: Line ~116 (SEBELUMNYA ERROR LINE 121 & 125)
        Preference(context).apply {  // FIX: BUKAN Preference(screen.context)!
            key = "feature_info"  // FIX: Tambah key!
            title = "Fitur Extension"
            summary = "✓ Self-healing domain\n✓ Live filter scraping\n✓ YouTube trailer\n✓ Quality selector\n✓ Filter cache"
            isEnabled = false  // FIX: isEnabled valid!
        }.also(screen::addPreference)
    }
}
