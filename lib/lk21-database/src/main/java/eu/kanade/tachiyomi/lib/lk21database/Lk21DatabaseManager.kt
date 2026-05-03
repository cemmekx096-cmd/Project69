package eu.kanade.tachiyomi.lib.lk21database

import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lk21DatabaseManager
 *
 * Mengelola database film lokal untuk LK21Movies & LK21Series.
 * - Auto-cek index.json setiap CHECK_INTERVAL_MS (14 hari)
 * - Download lk21_data.json hanya kalau versi berubah
 * - Search & filter lokal (offline, cepat)
 *
 * Usage:
 *   private val db by lazy { Lk21DatabaseManager(context, client) }
 *   db.searchLocal(query, page, type = "movie")
 */
class Lk21DatabaseManager(
    private val context: Context,
    private val client: OkHttpClient,
) {

    // ── Config ────────────────────────────────────────────────────────────────

    companion object {
        // Ganti URL ini setelah upload ke GitHub
        const val INDEX_URL = "https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/lk21_index.json"

        const val PAGE_SIZE = 24
        private const val CHECK_INTERVAL_MS = 14L * 24 * 60 * 60 * 1000 // 14 hari
        private const val DB_FILE_NAME = "lk21_database.json"
        private const val PREF_NAME = "lk21_db_prefs"
        private const val PREF_VERSION = "db_version"
        private const val PREF_LAST_CHECK = "db_last_check"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    private val dbFile: File
        get() = File(context.filesDir, DB_FILE_NAME)

    // Cache in-memory supaya tidak parse ulang tiap search
    private var cachedFilms: List<Lk21Film>? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Cek apakah database sudah tersedia lokal
     */
    val isAvailable: Boolean
        get() = dbFile.exists() && dbFile.length() > 0

    /**
     * Search lokal berdasarkan query text dan type film.
     * @param query keyword pencarian
     * @param page halaman (1-based)
     * @param type "movie", "series", atau "" untuk semua
     */
    fun searchLocal(query: String, page: Int, type: String = ""): AnimesPage {
        val films = loadFilms()
        val filtered = films.filter { film ->
            val matchType = type.isEmpty() || film.type == type
            val matchQuery = query.isEmpty() || film.title.contains(query, ignoreCase = true)
            matchType && matchQuery
        }

        val fromIndex = (page - 1) * PAGE_SIZE
        val toIndex = minOf(fromIndex + PAGE_SIZE, filtered.size)

        if (fromIndex >= filtered.size) {
            return AnimesPage(emptyList(), false)
        }

        val pageItems = filtered.subList(fromIndex, toIndex)
        val animes = pageItems.map { it.toSAnime() }
        val hasNextPage = toIndex < filtered.size

        return AnimesPage(animes, hasNextPage)
    }

    /**
     * Trigger cek update — panggil ini saat extension init atau user buka settings.
     * Hanya fetch jika sudah lewat CHECK_INTERVAL_MS sejak cek terakhir.
     * Jalankan di background thread.
     */
    fun checkForUpdates() {
        val lastCheck = prefs.getLong(PREF_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()

        if ((now - lastCheck) < CHECK_INTERVAL_MS) return

        try {
            val index = fetchIndex() ?: return
            prefs.edit().putLong(PREF_LAST_CHECK, now).apply()

            val currentVersion = prefs.getString(PREF_VERSION, "") ?: ""
            if (index.version == currentVersion && dbFile.exists()) return

            // Versi berbeda → download database baru
            downloadDatabase(index)
        } catch (_: Exception) {
            // Gagal? Tetap pakai data lama, coba lagi next interval
        }
    }

    /**
     * Force download database (untuk tombol "Update Database" di settings)
     * @return true kalau berhasil
     */
    fun forceUpdate(): Boolean {
        return try {
            val index = fetchIndex() ?: return false
            downloadDatabase(index)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Hapus database lokal + reset prefs
     */
    fun clearDatabase() {
        dbFile.delete()
        cachedFilms = null
        prefs.edit()
            .remove(PREF_VERSION)
            .remove(PREF_LAST_CHECK)
            .apply()
    }

    /**
     * Info versi database yang sedang aktif
     */
    fun currentVersion(): String = prefs.getString(PREF_VERSION, "Belum didownload") ?: "Belum didownload"

    /**
     * Jumlah film di database lokal
     */
    fun totalFilms(): Int = loadFilms().size

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun fetchIndex(): Lk21DbIndex? {
        val response = client.newCall(Request.Builder().url(INDEX_URL).build()).execute()
        if (!response.isSuccessful) return null
        val json = JSONObject(response.body.string())
        return Lk21DbIndex(
            version = json.getString("version"),
            total = json.getInt("total"),
            url = json.getString("url"),
        )
    }

    private fun downloadDatabase(index: Lk21DbIndex) {
        val response = client.newCall(Request.Builder().url(index.url).build()).execute()
        if (!response.isSuccessful) return

        // Tulis ke file
        dbFile.writeText(response.body.string())

        // Reset cache in-memory
        cachedFilms = null

        // Simpan versi baru
        prefs.edit().putString(PREF_VERSION, index.version).apply()
    }

    private fun loadFilms(): List<Lk21Film> {
        cachedFilms?.let { return it }

        if (!dbFile.exists()) return emptyList()

        return try {
            val json = JSONObject(dbFile.readText())
            val arr: JSONArray = json.getJSONArray("data")
            val result = mutableListOf<Lk21Film>()

            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                result.add(
                    Lk21Film(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        slug = item.optString("slug"),
                        poster = item.optString("poster"),
                        type = item.optString("type"),
                        year = item.optInt("year", 0),
                        quality = item.optString("quality"),
                        rating = item.optDouble("rating", 0.0),
                        runtime = item.optString("runtime"),
                        episode = item.optString("episode"),
                        season = item.optString("season"),
                        isComplete = item.optInt("is_complete", 0),
                    ),
                )
            }

            cachedFilms = result
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Extension function ────────────────────────────────────────────────────

    private fun Lk21Film.toSAnime(): SAnime = SAnime.create().apply {
        setUrlWithoutDomain("/$slug")
        title = this@toSAnime.title
        // poster base URL dari gudangvape
        thumbnail_url = "https://poster.lk21.party/wp-content/uploads/$poster"
    }
}
