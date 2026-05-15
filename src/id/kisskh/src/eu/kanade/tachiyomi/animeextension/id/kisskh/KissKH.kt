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
        val obj = JSONObject(response.body.string())
        val arr = obj.getJSONArray("data")
        val animes = (0 until arr.length()).map { arr.getJSONObject(it).toSAnime() }
        val totalCount = obj.getInt("totalCount")
        val pageSize = obj.getInt("pageSize")
        val page = obj.getInt("page")
        ReportLog.log("KissKH-Popular", "Fetched ${animes.size} items", LogLevel.DEBUG)
        return AnimesPage(animes, page * pageSize < totalCount)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) =
        GET("$apiUrl/DramaList/List?page=$page&type=0&sub=0&country=0&status=0&order=2", headers)

    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            GET("$apiUrl/DramaList/Search?q=${query.trim()}&type=0", headers)
        } else {
            val params = KissKHFilters.getSearchParameters(filters)
            GET(
                "$apiUrl/DramaList/List?page=$page&type=${params.type}" +
                    "&sub=${params.sub}&country=${params.country}" +
                    "&status=${params.status}&order=${params.order}",
                headers,
            )
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val body = response.body.string()
        val isSearch = response.request.url.toString().contains("/Search")
        return if (isSearch) {
            val arr = JSONArray(body)
            val animes = (0 until arr.length()).map { arr.getJSONObject(it).toSAnime() }
            ReportLog.log("KissKH-Search", "Found ${animes.size} results", LogLevel.DEBUG)
            AnimesPage(animes, false)
        } else {
            popularAnimeParse(response)
        }
    }

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime) =
        GET("$apiUrl/DramaList/Drama/${anime.url}?isq=false", headers)

    override fun animeDetailsParse(response: Response) = SAnime.create().apply {
        val obj = JSONObject(response.body.string())
        title = obj.getString("title")
        thumbnail_url = obj.optString("thumbnail")
        description = buildString {
            obj.optString("description").takeIf { it.isNotBlank() }?.let { append(it).append("\n\n") }
            obj.optString("country").takeIf { it.isNotBlank() }?.let { append("Country: $it\n") }
            obj.optString("status").takeIf { it.isNotBlank() }?.let { append("Status: $it\n") }
            obj.optString("type").takeIf { it.isNotBlank() }?.let { append("Type: $it\n") }
            obj.optString("releaseDate").take(10).takeIf { it.isNotBlank() }?.let { append("Release: $it\n") }
        }
        status = when (obj.optString("status").lowercase()) {
            "ongoing" -> SAnime.ONGOING
            "completed" -> SAnime.COMPLETED
            "upcoming" -> SAnime.ON_HIATUS
            else -> SAnime.UNKNOWN
        }
        genre = obj.optString("type")
        ReportLog.log("KissKH-Detail", "Parsed: $title", LogLevel.DEBUG)
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime) =
        GET("$apiUrl/DramaList/Drama/${anime.url}?isq=false", headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val obj = JSONObject(response.body.string())
        val episodes = obj.getJSONArray("episodes")
        val list = mutableListOf<SEpisode>()
        val dramaId = obj.getInt("id")

        for (i in 0 until episodes.length()) {
            val ep = episodes.getJSONObject(i)
            val epId = ep.getInt("id")
            val epNum = ep.getDouble("number")
            val hasSub = ep.getInt("sub") > 0

            val episode = SEpisode.create().apply {
                // url = "dramaId/episodeId" untuk dipakai di videoListRequest
                url = "$dramaId/$epId"
                name = "Episode ${epNum.toInt()}" + (if (!hasSub) " (No Sub)" else "")
                episode_number = epNum.toFloat()
                date_upload = System.currentTimeMillis()
            }
            list.add(episode)
        }

        ReportLog.log("KissKH-Episodes", "Found ${list.size} episodes for drama $dramaId", LogLevel.DEBUG)
        return list.reversed()
    }

    // ============================ Video Links =============================

    override fun videoListRequest(episode: SEpisode): Request {
        val epId = episode.url.substringAfter("/")
        val kkey = KissKHKey.videoKey(epId.toInt())
        return GET(
            "$apiUrl/DramaList/Episode/$epId.png?err=false&ts=null&time=null&kkey=$kkey",
            headers,
        )
    }

    override fun videoListParse(response: Response): List<Video> {
        val obj = JSONObject(response.body.string())
        val videoUrl = obj.optString("Video").takeIf { it.isNotBlank() }
            ?: return emptyList<Video>().also {
                ReportLog.log("KissKH-Video", "Empty video URL", LogLevel.WARN)
            }

        // Ambil episode ID dari request URL
        val epId = response.request.url.pathSegments
            .last().removeSuffix(".png").toIntOrNull()
            ?: return listOf(Video(videoUrl, "KissKH", videoUrl))

        // Fetch subtitles
        val subtitleTracks = fetchSubtitles(epId)

        ReportLog.log("KissKH-Video", "Video: $videoUrl | Subs: ${subtitleTracks.size}", LogLevel.DEBUG)

        return listOf(
            Video(
                url = videoUrl,
                quality = "KissKH",
                videoUrl = videoUrl,
                subtitleTracks = subtitleTracks,
            ),
        )
    }

    private fun fetchSubtitles(epId: Int): List<Track> {
        return try {
            val kkey = KissKHKey.subKey(epId)
            val subHeaders = Headers.Builder()
                .add("Accept", "application/json, text/plain, */*")
                .build()
            val response = client.newCall(
                GET("$apiUrl/Sub/$epId?kkey=$kkey", subHeaders),
            ).execute()

            val arr = JSONArray(response.body.string())
            val preferredLang = preferences.getString(PREF_SUB_KEY, PREF_SUB_DEFAULT)!!

            val tracks = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val sub = arr.getJSONObject(i)
                val lang = sub.getString("land")
                val label = sub.getString("label")
                val src = sub.getString("src")
                tracks.add(Track(src, label))
                ReportLog.log("KissKH-Sub", "Sub: $label ($lang)", LogLevel.DEBUG)
            }

            // Sort: preferred lang duluan
            tracks.sortedWith(compareBy { if (it.lang == preferredLang) 0 else 1 })
        } catch (e: Exception) {
            ReportLog.reportError("KissKH-Sub", "Failed: ${e.message}")
            emptyList()
        }
    }

    // ============================== Helpers ===============================

    private fun JSONObject.toSAnime() = SAnime.create().apply {
        val dramaId = getInt("id")
        val dramaTitle = getString("title")
        url = dramaId.toString()
        title = dramaTitle
        thumbnail_url = optString("thumbnail").takeIf { it.isNotBlank() }
        status = SAnime.UNKNOWN
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
