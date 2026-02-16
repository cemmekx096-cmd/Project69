package eu.kanade.tachiyomi.animeextension.id.SOURCENAME

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen

object SOURCENAMEPreferences {

    // =========================================================
    // Keys
    // =========================================================
    const val PREF_BASE_URL_KEY   = "pref_base_url"
    const val PREF_QUALITY_KEY    = "pref_quality"
    const val PREF_USER_AGENT_KEY = "pref_user_agent"
    const val PREF_TIMEOUT_KEY    = "pref_timeout"

    // =========================================================
    // Defaults
    // =========================================================
    const val PREF_BASE_URL_DEFAULT   = "https://DOMAIN.com"
    const val PREF_QUALITY_DEFAULT    = "720p"
    const val PREF_USER_AGENT_DEFAULT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/120.0.0.0 Safari/537.36"
    const val PREF_TIMEOUT_DEFAULT    = "30"

    // =========================================================
    // Options
    // =========================================================
    private val QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
    private val QUALITY_VALUES  = arrayOf("1080p", "720p", "480p", "360p")

    private val TIMEOUT_ENTRIES = arrayOf("15 detik", "30 detik", "60 detik", "90 detik", "120 detik")
    private val TIMEOUT_VALUES  = arrayOf("15", "30", "60", "90", "120")

    // =========================================================
    // Developer Info
    // =========================================================
    private const val DEV_NAME       = "User404"
    private const val DEV_GITHUB_URL = "https://github.com/cemmekx096-cmd/Project69"
    private const val DEV_GITHUB_SUMMARY = "github.com/cemmekx096-cmd/Project69"

    // =========================================================
    // Setup
    // =========================================================
    fun setupPreferences(screen: PreferenceScreen, preferences: SharedPreferences) {

        // ── Developer Card ────────────────────────────────────
        Preference(screen.context).apply {
            title   = "🛠 $DEV_NAME"
            summary = "Tap untuk membuka repositori\n$DEV_GITHUB_SUMMARY"
            // icon_logo.png harus ada di res/drawable/icon_logo.png
            icon    = ContextCompat.getDrawable(screen.context, R.drawable.icon_logo)

            setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DEV_GITHUB_URL))
                screen.context.startActivity(intent)
                true
            }
        }.also(screen::addPreference)

        // ── Separator ─────────────────────────────────────────
        Preference(screen.context).apply {
            title   = "⚙ Pengaturan"
            summary = "Sesuaikan extension sesuai kebutuhanmu"
            isSelectable = false
        }.also(screen::addPreference)

        // ── Base URL ──────────────────────────────────────────
        EditTextPreference(screen.context).apply {
            key          = PREF_BASE_URL_KEY
            title        = "Base URL"
            summary      = "Ganti domain jika situs diblokir\nSekarang: ${
                preferences.getString(PREF_BASE_URL_KEY, PREF_BASE_URL_DEFAULT)
            }"
            setDefaultValue(PREF_BASE_URL_DEFAULT)
            dialogTitle   = "Masukkan Base URL"
            dialogMessage = "Contoh: https://DOMAIN.com\nPastikan tidak ada slash (/) di akhir URL."

            setOnPreferenceChangeListener { _, newValue ->
                val url = (newValue as String).trimEnd('/')
                preferences.edit().putString(PREF_BASE_URL_KEY, url).apply()
                summary = "Ganti domain jika situs diblokir\nSekarang: $url"
                true
            }
        }.also(screen::addPreference)

        // ── Preferred Quality ─────────────────────────────────
        ListPreference(screen.context).apply {
            key          = PREF_QUALITY_KEY
            title        = "Kualitas Video"
            summary      = "Kualitas yang diprioritaskan: %s"
            entries      = QUALITY_ENTRIES
            entryValues  = QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PREF_QUALITY_KEY, newValue as String).apply()
                true
            }
        }.also(screen::addPreference)

        // ── Timeout ───────────────────────────────────────────
        ListPreference(screen.context).apply {
            key          = PREF_TIMEOUT_KEY
            title        = "Timeout Koneksi"
            summary      = "Waktu tunggu sebelum request gagal: %s"
            entries      = TIMEOUT_ENTRIES
            entryValues  = TIMEOUT_VALUES
            setDefaultValue(PREF_TIMEOUT_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(PREF_TIMEOUT_KEY, newValue as String).apply()
                true
            }
        }.also(screen::addPreference)

        // ── Custom User Agent ─────────────────────────────────
        EditTextPreference(screen.context).apply {
            key          = PREF_USER_AGENT_KEY
            title        = "Custom User Agent"
            summary      = "Kosongkan untuk menggunakan default"
            setDefaultValue(PREF_USER_AGENT_DEFAULT)
            dialogTitle   = "Masukkan User Agent"
            dialogMessage = "Kosongkan untuk kembali ke default."

            setOnPreferenceChangeListener { _, newValue ->
                val ua = (newValue as String).ifBlank { PREF_USER_AGENT_DEFAULT }
                preferences.edit().putString(PREF_USER_AGENT_KEY, ua).apply()
                true
            }
        }.also(screen::addPreference)
    }
}
