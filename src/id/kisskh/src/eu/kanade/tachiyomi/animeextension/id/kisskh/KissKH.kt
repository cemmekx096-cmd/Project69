package eu.kanade.tachiyomi.animeextension.id.kisskh

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class KissKH : ConfigurableAnimeSource, AnimeHttpSource() {

    override val name = "KissKH"
    override val lang = "id"
    override val supportsLatest = true
    override val baseUrl = "https://kisskh.do"
    private val apiUrl = "$baseUrl/api"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val client: OkHttpClient by lazy {
        network.client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun headersBuilder() = Headers.Builder()
        .add("Accept", "application/json, text/plain, */*")
        .add("User-Agent", UA)
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int) =
        GET("$apiUrl/DramaList/List?page=$page&type=0&sub=0&country=0&status=0&order=1", headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        ReportLog.log("KissKH-Popular", "Parsing page...", LogLevel.DEBUG)
        return parseAnimeList(response, "KissKH-Popular")
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) =
        GET("$apiUrl/DramaList/List?page=$page&type=0&sub=0&country=0&status=0&order=2", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage {
        ReportLog.log("KissKH-Latest", "Parsing page...", LogLevel.DEBUG)
        return parseAnimeList(response, "KissKH-Latest")
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) {
            ReportLog.log("KissKH-Search", "Query: $query", LogLevel.DEBUG)
            return GET("$apiUrl/DramaList/Search?q=${query.trim()}&type=0", headers)
        }
        val params = KissKHFilters.getSearchParameters(filters)
        val url = "$apiUrl/DramaList/List?page=$page&type=${params.type}" +
            "&sub=${params.sub}&country=${params.country}" +
            "&status=${params.status}&order=${params.order}"
        ReportLog.log("KissKH-Search", "Filter URL: $url", LogLevel.DEBUG)
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val body = response.body.string()
        return if (response.request.url.toString().contains("/Search")) {
            val arr = JSONArray(body)
            val animes = (0 until arr.length()).map { arr.getJSONObject(it).toSAnime() }
            ReportLog.log("KissKH-Search", "Results: ${animes.size}", LogLevel.DEBUG)
            AnimesPage(animes, false)
        } else {
            parseAnimeList(response, "KissKH-Search")
        }
    }

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime) =
        GET("$apiUrl/DramaList/Drama/${anime.url}?isq=false", headers)

    override fun animeDetailsParse(response: Response) = SAnime.create().apply {
        val obj = JSONObject(response.body.string())
        title = obj.getString("title")
        thumbnail_url = obj.optString("thumbnail")
        description = obj.optString("description")
        genre = obj.optString("type")
        status = when (obj.optString("status").lowercase()) {
            "ongoing" -> SAnime.ONGOING
            "completed" -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
        ReportLog.log("KissKH-Detail", "Parsed: $title | Status: $status", LogLevel.DEBUG)
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime) =
        GET("$apiUrl/DramaList/Drama/${anime.url}?isq=false", headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val obj = JSONObject(response.body.string())
        val episodes = obj.getJSONArray("episodes")
        val list = (0 until episodes.length()).map { i ->
            val ep = episodes.getJSONObject(i)
            SEpisode.create().apply {
                url = ep.getInt("id").toString()
                name = "Episode ${ep.getInt("number")}"
                episode_number = ep.getDouble("number").toFloat()
            }
        }.reversed()
        ReportLog.log("KissKH-Episodes", "Parsed: ${list.size} episodes", LogLevel.INFO)
        return list
    }

    // ============================ Video Links =============================

    override fun videoListRequest(episode: SEpisode): Request {
        val epId = episode.url.toInt()
        ReportLog.log("KissKH-Video", "Step 1: Building kkey | epId=$epId", LogLevel.DEBUG)
        val kkey = try {
            val k = KissKHKey.videoKey(epId)
            ReportLog.log("KissKH-Video", "Step 1 OK: kkey=$k", LogLevel.DEBUG)
            k
        } catch (e: Exception) {
            ReportLog.log("KissKH-Video", "Step 1 FAILED: ${e.message}", LogLevel.ERROR)
            ""
        }
        val url = "$apiUrl/DramaList/Episode/$epId.png?err=false&ts=null&time=null&kkey=$kkey"
        ReportLog.log("KissKH-Video", "Step 2: API URL = $url", LogLevel.DEBUG)
        return GET(url, headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val body = response.body.string()
        ReportLog.log("KissKH-Video", "Step 3 Response: $body", LogLevel.DEBUG)
        val obj = JSONObject(body)
        val videoUrl = obj.getString("Video")
        ReportLog.log("KissKH-Video", "Step 4 OK: videoUrl=$videoUrl", LogLevel.INFO)
        return listOf(Video(videoUrl, "KissKH", videoUrl))
    }

    // =============================== Subtitle =============================

    override fun subtitleListRequest(episode: SEpisode): Request {
        val epId = episode.url.toInt()
        ReportLog.log("KissKH-Sub", "Step 1: Building sub kkey | epId=$epId", LogLevel.DEBUG)
        val kkey = try {
            val k = KissKHKey.subKey(epId)
            ReportLog.log("KissKH-Sub", "Step 1 OK: kkey=$k", LogLevel.DEBUG)
            k
        } catch (e: Exception) {
            ReportLog.log("KissKH-Sub", "Step 1 FAILED: ${e.message}", LogLevel.ERROR)
            ""
        }
        val url = "$apiUrl/Sub/$epId?kkey=$kkey"
        ReportLog.log("KissKH-Sub", "Step 2: URL=$url", LogLevel.DEBUG)
        return GET(url, headers)
    }

    override fun subtitleListParse(response: Response): List<Track> {
        val body = response.body.string()
        ReportLog.log("KissKH-Sub", "Step 3 Response: $body", LogLevel.DEBUG)
        val arr = JSONArray(body)
        val preferredLang = preferences.getString(PREF_SUB_KEY, PREF_SUB_DEFAULT)!!
        val tracks = (0 until arr.length()).map { i ->
            val sub = arr.getJSONObject(i)
            Track(sub.getString("src"), sub.getString("label"))
        }.sortedBy { if (it.lang == preferredLang) 0 else 1 }
        ReportLog.log("KissKH-Sub", "Step 4 OK: ${tracks.size} tracks | preferred=$preferredLang", LogLevel.INFO)
        return tracks
    }

    // ============================== Helpers ===============================

    private fun parseAnimeList(response: Response, tag: String): AnimesPage {
        val body = response.body.string()
        val obj = JSONObject(body)
        val arr = obj.getJSONArray("data")
        val animes = (0 until arr.length()).map { arr.getJSONObject(it).toSAnime() }
        val totalCount = obj.getInt("totalCount")
        val pageSize = obj.getInt("pageSize")
        val page = obj.getInt("page")
        ReportLog.log(tag, "Page=$page | Count=${animes.size} | Total=$totalCount", LogLevel.DEBUG)
        return AnimesPage(animes, page * pageSize < totalCount)
    }

    private fun JSONObject.toSAnime() = SAnime.create().apply {
        url = getInt("id").toString()
        title = getString("title")
        thumbnail_url = optString("thumbnail").takeIf { it.isNotBlank() }
    }

    // ============================== Filters ===============================

    override fun getFilterList() = KissKHFilters.getFilterList()

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_SUB_KEY
            title = "Preferred Subtitle Language"
            entries = arrayOf("Indonesia", "English", "Thai", "Arabic", "Malay", "Khmer")
            entryValues = arrayOf("id", "en", "th", "ar", "ms", "km")
            setDefaultValue(PREF_SUB_DEFAULT)
            summary = "%s"
        }.also(screen::addPreference)
    }

    companion object {
        private const val UA = "Mozilla/5.0 (Android 16; Mobile; rv:150.0) Gecko/150.0 Firefox/150.0"
        private const val PREF_SUB_KEY = "preferred_sub"
        private const val PREF_SUB_DEFAULT = "id"
    }
}
