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
        val body = response.body.string()
        val obj = JSONObject(body)
        val arr = obj.getJSONArray("data")
        val animes = (0 until arr.length()).map { arr.getJSONObject(it).toSAnime() }
        val totalCount = obj.getInt("totalCount")
        val pageSize = obj.getInt("pageSize")
        val page = obj.getInt("page")
        ReportLog.log("KissKH-Popular", "Page=$page | Count=${animes.size} | Total=$totalCount", LogLevel.DEBUG)
        return AnimesPage(animes, page * pageSize < totalCount)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) =
        GET("$apiUrl/DramaList/List?page=$page&type=0&sub=0&country=0&status=0&order=2", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage {
        val body = response.body.string()
        val obj = JSONObject(body)
        val arr = obj.getJSONArray("data")
        val animes = (0 until arr.length()).map { arr.getJSONObject(it).toSAnime() }
        val totalCount = obj.getInt("totalCount")
        val pageSize = obj.getInt("pageSize")
        val page = obj.getInt("page")
        ReportLog.log("KissKH-Latest", "Page=$page | Count=${animes.size} | Total=$totalCount", LogLevel.DEBUG)
        return AnimesPage(animes, page * pageSize < totalCount)
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            ReportLog.log("KissKH-Search", "Query: $query", LogLevel.DEBUG)
            GET("$apiUrl/DramaList/Search?q=${query.trim()}&type=0", headers)
        } else {
            val params = KissKHFilters.getSearchParameters(filters)
            val url = "$apiUrl/DramaList/List?page=$page&type=${params.type}" +
                "&sub=${params.sub}&country=${params.country}" +
                "&status=${params.status}&order=${params.order}"
            ReportLog.log("KissKH-Search", "Filter URL: $url", LogLevel.DEBUG)
            GET(url, headers)
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val body = response.body.string()
        val isSearch = response.request.url.toString().contains("/Search")
        return if (isSearch) {
            val arr = JSONArray(body)
            val animes = (0 until arr.length()).map { arr.getJSONObject(it).toSAnime() }
            ReportLog.log("KissKH-Search", "Results: ${animes.size}", LogLevel.DEBUG)
            AnimesPage(animes, false)
        } else {
            val obj = JSONObject(body)
            val arr = obj.getJSONArray("data")
            val animes = (0 until arr.length()).map { arr.getJSONObject(it).toSAnime() }
            val totalCount = obj.getInt("totalCount")
            val pageSize = obj.getInt("pageSize")
            val page = obj.getInt("page")
            ReportLog.log("KissKH-Search", "Filter page=$page | Count=${animes.size} | Total=$totalCount", LogLevel.DEBUG)
            AnimesPage(animes, page * pageSize < totalCount)
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
        ReportLog.log("KissKH-Detail", "Parsed: $title | Status: $status", LogLevel.DEBUG)
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime) =
        GET("$apiUrl/DramaList/Drama/${anime.url}?isq=false", headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val obj = JSONObject(response.body.string())
        val episodes = obj.getJSONArray("episodes")
        val dramaId = obj.getInt("id")
        val dramaTitle = obj.getString("title")
        val list = mutableListOf<SEpisode>()

        ReportLog.log("KissKH-Episodes", "Drama: $dramaTitle (id=$dramaId) | Episodes: ${episodes.length()}", LogLevel.DEBUG)

        for (i in 0 until episodes.length()) {
            val ep = episodes.getJSONObject(i)
            val epId = ep.getInt("id")
            val epNum = ep.getDouble("number")
            val hasSub = ep.getInt("sub") > 0

            ReportLog.log("KissKH-Episodes", "Ep${epNum.toInt()} | epId=$epId | hasSub=$hasSub", LogLevel.DEBUG)

            val episode = SEpisode.create().apply {
                url = "$dramaId/$epId"
                name = "Episode ${epNum.toInt()}" + (if (!hasSub) " (No Sub)" else "")
                episode_number = epNum.toFloat()
                date_upload = System.currentTimeMillis()
            }
            list.add(episode)
        }

        ReportLog.log("KissKH-Episodes", "Total parsed: ${list.size}", LogLevel.INFO)
        return list.reversed()
    }

    // ============================ Video Links =============================

    override fun videoListRequest(episode: SEpisode): Request {
        val tracker = FeatureTracker("KissKH-Video")
        tracker.start()

        val parts = episode.url.split("/")
        val dramaId = parts[0]
        val epId = parts[1]

        // ── Step 1: Build kkey ─────────────────────────────────
        tracker.debug("Step 1: Building kkey | epId=$epId")
        val kkey = try {
            val k = KissKHKey.videoKey(epId.toInt())
            tracker.debug("Step 1 OK: kkey=$k")
            k
        } catch (e: Exception) {
            tracker.error("Step 1 FAILED: ${e.message}")
            return GET("$apiUrl/DramaList/Episode/$epId.png?err=false&ts=null&time=null&kkey=", headers)
        }

        // ── Step 2: Build API URL ──────────────────────────────
        val url = "$apiUrl/DramaList/Episode/$epId.png?err=false&ts=null&time=null&kkey=$kkey"
        tracker.debug("Step 2: API URL = $url")

        return GET(url, headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val perf = PerformanceTracker("KissKH-VideoListParse")
        val tracker = FeatureTracker("KissKH-Video")
        perf.start()

        // ── Step 3: Read API response ──────────────────────────
        tracker.debug("Step 3: Reading video API response")
        val body = response.body.string()
        tracker.debug("Step 3 Response: $body")

        val obj = try {
            JSONObject(body)
        } catch (e: Exception) {
            tracker.error("Step 3 FAILED - JSON parse error: ${e.message}")
            perf.end()
            return emptyList()
        }

        // ── Step 4: Extract M3U8 URL ───────────────────────────
        tracker.debug("Step 4: Extracting M3U8 URL")
        val videoUrl = obj.optString("Video").takeIf { it.isNotBlank() }
        if (videoUrl == null) {
            tracker.error("Step 4 FAILED - Video URL is empty! Full response: $body")
            perf.end()
            return emptyList()
        }
        val isM3u8 = videoUrl.contains(".m3u8")
        tracker.debug("Step 4 OK: videoUrl=$videoUrl | isM3U8=$isM3u8")

        // ── Step 5: Get episode ID from request URL ────────────
        tracker.debug("Step 5: Extracting epId from request URL")
        val epId = response.request.url.pathSegments
            .last().removeSuffix(".png").toIntOrNull()
        if (epId == null) {
            tracker.warn("Step 5 WARN - Cannot parse epId, skipping subtitles")
            perf.end()
            return listOf(Video(videoUrl, "KissKH", videoUrl))
        }
        tracker.debug("Step 5 OK: epId=$epId")

        // ── Step 6: Fetch subtitles ────────────────────────────
        tracker.debug("Step 6: Fetching subtitles for epId=$epId")
        val subtitleTracks = fetchSubtitles(epId, tracker)
        tracker.debug("Step 6 OK: ${subtitleTracks.size} subtitle track(s) found")

        // ── Step 7: Build Video object ─────────────────────────
        tracker.success("Step 7: Video ready | url=$videoUrl | subs=${subtitleTracks.size}")
        perf.end()

        return listOf(
            Video(
                videoUrl,
                "KissKH",
                videoUrl,
                subtitleTracks = subtitleTracks,
            ),
        )
    }

    private fun fetchSubtitles(epId: Int, tracker: FeatureTracker? = null): List<Track> {
        val subTracker = FeatureTracker("KissKH-Subtitle")
        subTracker.start()

        return try {
            // ── Sub Step 1: Build sub kkey ─────────────────────
            subTracker.debug("Sub Step 1: Building sub kkey | epId=$epId")
            val kkey = KissKHKey.subKey(epId)
            subTracker.debug("Sub Step 1 OK: kkey=$kkey")

            // ── Sub Step 2: Build sub API URL ──────────────────
            val subUrl = "$apiUrl/Sub/$epId?kkey=$kkey"
            subTracker.debug("Sub Step 2: URL=$subUrl")

            // ── Sub Step 3: Fetch subtitle list ────────────────
            val subHeaders = Headers.Builder()
                .add("Accept", "application/json, text/plain, */*")
                .build()
            val response = client.newCall(GET(subUrl, subHeaders)).execute()
            val body = response.body.string()
            subTracker.debug("Sub Step 3 Response: $body")

            // ── Sub Step 4: Parse subtitle list ────────────────
            val arr = JSONArray(body)
            subTracker.debug("Sub Step 4: Found ${arr.length()} subtitle(s)")

            val preferredLang = preferences.getString(PREF_SUB_KEY, PREF_SUB_DEFAULT)!!
            subTracker.debug("Sub Step 4: Preferred lang=$preferredLang")

            val tracks = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val sub = arr.getJSONObject(i)
                val lang = sub.getString("land")
                val label = sub.getString("label")
                val src = sub.getString("src")
                subTracker.debug("Sub Step 4: Track[$i] lang=$lang | label=$label | src=$src")
                tracks.add(Track(src, label))
            }

            // ── Sub Step 5: Sort preferred lang first ──────────
            val sorted = tracks.sortedWith(compareBy { if (it.lang == preferredLang) 0 else 1 })
            subTracker.success("Sub Step 5 OK: ${sorted.size} tracks | preferred=$preferredLang first")
            sorted
        } catch (e: Exception) {
            subTracker.error("Subtitle fetch FAILED: ${e.message}")
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
