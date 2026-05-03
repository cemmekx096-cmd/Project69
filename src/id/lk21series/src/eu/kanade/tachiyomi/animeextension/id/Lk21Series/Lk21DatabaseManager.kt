package eu.kanade.tachiyomi.animeextension.id.lk21series

import android.app.Application
import android.content.SharedPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Lk21Film(
    val title: String,
    val slug: String,
    val poster: String,
    val type: String,
)

data class Lk21DbIndex(
    val version: String,
    val total: Int,
    val url: String,
)

class Lk21DatabaseManager(
    private val app: Application,
    private val client: OkHttpClient,
) {
    companion object {
        const val INDEX_URL = "https://raw.githubusercontent.com/cemmekx096-cmd/Project69/refs/heads/main/lk21data/lk21_index.json"
        const val PAGE_SIZE = 24
        private const val CHECK_INTERVAL_MS = 14L * 24 * 60 * 60 * 1000
        private const val DB_FILE_NAME = "lk21_database.json"
        private const val PREF_NAME = "lk21_db_prefs"
        private const val PREF_VERSION = "db_version"
        private const val PREF_LAST_CHECK = "db_last_check"
    }

    private val prefs: SharedPreferences by lazy {
        app.getSharedPreferences(PREF_NAME, 0)
    }

    private val dbFile: File
        get() = File(app.filesDir, DB_FILE_NAME)

    private var cachedFilms: List<Lk21Film>? = null

    val isAvailable: Boolean
        get() = dbFile.exists() && dbFile.length() > 0

    fun searchLocal(query: String, page: Int, type: String = ""): Pair<List<Lk21Film>, Boolean> {
        val films = loadFilms()
        val filtered = films.filter {
            (type.isEmpty() || it.type == type) &&
                (query.isEmpty() || it.title.contains(query, ignoreCase = true))
        }
        val fromIndex = (page - 1) * PAGE_SIZE
        val toIndex = minOf(fromIndex + PAGE_SIZE, filtered.size)
        if (fromIndex >= filtered.size) return Pair(emptyList(), false)
        return Pair(filtered.subList(fromIndex, toIndex), toIndex < filtered.size)
    }

    fun checkForUpdates() {
        val now = System.currentTimeMillis()
        if ((now - prefs.getLong(PREF_LAST_CHECK, 0L)) < CHECK_INTERVAL_MS) return
        try {
            val index = fetchIndex() ?: return
            prefs.edit().putLong(PREF_LAST_CHECK, now).apply()
            if (index.version == (prefs.getString(PREF_VERSION, "") ?: "") && dbFile.exists()) return
            downloadDatabase(index)
        } catch (_: Exception) { }
    }

    fun forceUpdate(): Boolean = try {
        val index = fetchIndex() ?: return false
        downloadDatabase(index); true
    } catch (_: Exception) { false }

    fun clearDatabase() {
        dbFile.delete(); cachedFilms = null
        prefs.edit().remove(PREF_VERSION).remove(PREF_LAST_CHECK).apply()
    }

    fun currentVersion(): String = prefs.getString(PREF_VERSION, "Belum didownload") ?: "Belum didownload"
    fun totalFilms(): Int = loadFilms().size

    private fun fetchIndex(): Lk21DbIndex? {
        val res = client.newCall(Request.Builder().url(INDEX_URL).build()).execute()
        if (!res.isSuccessful) return null
        val json = JSONObject(res.body.string())
        return Lk21DbIndex(json.getString("version"), json.getInt("total"), json.getString("url"))
    }

    private fun downloadDatabase(index: Lk21DbIndex) {
        val res = client.newCall(Request.Builder().url(index.url).build()).execute()
        if (!res.isSuccessful) return
        dbFile.writeText(res.body.string())
        cachedFilms = null
        prefs.edit().putString(PREF_VERSION, index.version).apply()
    }

    private fun loadFilms(): List<Lk21Film> {
        cachedFilms?.let { return it }
        if (!dbFile.exists()) return emptyList()
        return try {
            val arr: JSONArray = JSONObject(dbFile.readText()).getJSONArray("data")
            val result = (0 until arr.length()).map {
                val item = arr.getJSONObject(it)
                Lk21Film(item.optString("title"), item.optString("slug"), item.optString("poster"), item.optString("type"))
            }
            cachedFilms = result; result
        } catch (_: Exception) { emptyList() }
    }
}
