package eu.kanade.tachiyomi.lib.lk21database

import android.content.Context
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Lk21DatabaseManager(
    private val context: Context,
    private val client: OkHttpClient,
) {

    companion object {
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

    private var cachedFilms: List<Lk21Film>? = null

    // ── Public API ────────────────────────────────────────────────────────────

    val isAvailable: Boolean
        get() = dbFile.exists() && dbFile.length() > 0

    /**
     * Search lokal — return Pair<List<Lk21Film>, hasNextPage>
     * Konversi ke SAnime dilakukan di extension masing-masing
     */
    fun searchLocal(query: String, page: Int, type: String = ""): Pair<List<Lk21Film>, Boolean> {
        val films = loadFilms()
        val filtered = films.filter { film ->
            val matchType = type.isEmpty() || film.type == type
            val matchQuery = query.isEmpty() || film.title.contains(query, ignoreCase = true)
            matchType && matchQuery
        }

        val fromIndex = (page - 1) * PAGE_SIZE
        val toIndex = minOf(fromIndex + PAGE_SIZE, filtered.size)

        if (fromIndex >= filtered.size) return Pair(emptyList(), false)

        return Pair(filtered.subList(fromIndex, toIndex), toIndex < filtered.size)
    }

    fun checkForUpdates() {
        val lastCheck = prefs.getLong(PREF_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        if ((now - lastCheck) < CHECK_INTERVAL_MS) return

        try {
            val index = fetchIndex() ?: return
            prefs.edit().putLong(PREF_LAST_CHECK, now).apply()

            val currentVersion = prefs.getString(PREF_VERSION, "") ?: ""
            if (index.version == currentVersion && dbFile.exists()) return

            downloadDatabase(index)
        } catch (_: Exception) { }
    }

    fun forceUpdate(): Boolean {
        return try {
            val index = fetchIndex() ?: return false
            downloadDatabase(index)
            true
        } catch (_: Exception) { false }
    }

    fun clearDatabase() {
        dbFile.delete()
        cachedFilms = null
        prefs.edit().remove(PREF_VERSION).remove(PREF_LAST_CHECK).apply()
    }

    fun currentVersion(): String = prefs.getString(PREF_VERSION, "Belum didownload") ?: "Belum didownload"

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
        dbFile.writeText(response.body.string())
        cachedFilms = null
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
                        title = item.optString("title"),
                        slug = item.optString("slug"),
                        poster = item.optString("poster"),
                        type = item.optString("type"),
                    ),
                )
            }

            cachedFilms = result
            result
        } catch (_: Exception) { emptyList() }
    }
}
