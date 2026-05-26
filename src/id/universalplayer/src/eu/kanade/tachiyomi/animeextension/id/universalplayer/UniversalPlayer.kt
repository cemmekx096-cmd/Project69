package eu.kanade.tachiyomi.animeextension.id.universalplayer

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animeextension.id.universalplayer.extractors.ExtractorFactory
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class UniversalPlayer : ConfigurableAnimeSource, AnimeHttpSource() {

    override val name = "Universal Player"
    override val lang = "id"
    override val supportsLatest = false
    override val baseUrl = "https://raw.githubusercontent.com"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val githubRawUrl: String
        get() = preferences.getString(PREF_JSON_URL, "")?.trim() ?: ""

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        val perf = PerformanceTracker("UP-Popular")
        val tracker = FeatureTracker("UP-Popular")
        perf.start()
        tracker.start()

        val url = githubRawUrl.ifBlank {
            tracker.warn("GitHub Raw URL kosong, menggunakan fallback")
            FALLBACK_URL
        }
        tracker.debug("Fetching JSON dari: $url")
        return GET(url, headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val perf = PerformanceTracker("UP-PopularParse")
        val tracker = FeatureTracker("UP-PopularParse")
        perf.start()
        tracker.start()

        return try {
            val arr = JSONArray(response.body.string())
            tracker.debug("Total anime ditemukan: ${arr.length()}")

            val animes = (0 until arr.length()).map { i ->
                arr.getJSONObject(i).let { obj ->
                    SAnime.create().apply {
                        url = i.toString()
                        title = obj.getString("title")
                        thumbnail_url = obj.optString("poster", "").ifBlank { null }
                        status = SAnime.UNKNOWN
                    }.also {
                        tracker.debug("Parsed anime[$i]: ${it.title}")
                    }
                }
            }

            tracker.success("Berhasil parse ${animes.size} anime")
            perf.end()
            AnimesPage(animes, false)
        } catch (e: Exception) {
            tracker.error("Parse gagal: ${e.message}")
            perf.end()
            AnimesPage(emptyList(), false)
        }
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val tracker = FeatureTracker("UP-Search")
        tracker.start()
        tracker.debug("Query: '$query'")

        val url = githubRawUrl.ifBlank { FALLBACK_URL }
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        val tracker = FeatureTracker("UP-SearchParse")
        tracker.start()

        return try {
            val query = response.request.url.queryParameter("q") ?: ""
            val arr = JSONArray(response.body.string())

            val animes = (0 until arr.length()).mapIndexedNotNull { i, _ ->
                val obj = arr.getJSONObject(i)
                val title = obj.getString("title")

                // Filter berdasarkan query jika ada
                if (query.isNotBlank() && !title.contains(query, ignoreCase = true)) {
                    return@mapIndexedNotNull null
                }

                SAnime.create().apply {
                    url = i.toString()
                    this.title = title
                    thumbnail_url = obj.optString("poster", "").ifBlank { null }
                    status = SAnime.UNKNOWN
                }.also {
                    tracker.debug("Search result[$i]: $title")
                }
            }

            tracker.success("Search selesai: ${animes.size} hasil")
            AnimesPage(animes, false)
        } catch (e: Exception) {
            tracker.error("Search parse gagal: ${e.message}")
            AnimesPage(emptyList(), false)
        }
    }

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        val tracker = FeatureTracker("UP-Details")
        tracker.debug("Request detail untuk index: ${anime.url}")
        val url = githubRawUrl.ifBlank { FALLBACK_URL }
        return GET(url, headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val tracker = FeatureTracker("UP-DetailsParse")
        tracker.start()

        // Detail di-handle di getEpisodeList, ini return kosong
        tracker.debug("animeDetailsParse dipanggil, return default")
        return SAnime.create()
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request {
        val url = githubRawUrl.ifBlank { FALLBACK_URL }
        return GET(url, headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        return emptyList()
    }

    override fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val perf = PerformanceTracker("UP-Episodes")
        val tracker = FeatureTracker("UP-Episodes")
        perf.start()
        tracker.start()

        return try {
            val url = githubRawUrl.ifBlank { FALLBACK_URL }
            val response = client.newCall(GET(url, headers)).execute()
            val arr = JSONArray(response.body.string())

            val index = anime.url.toIntOrNull()
            if (index == null) {
                tracker.error("Index anime tidak valid: ${anime.url}")
                perf.end()
                return emptyList()
            }

            val animeObj = arr.getJSONObject(index)
            val episodes = animeObj.getJSONArray("episodes")
            tracker.debug("Anime: ${animeObj.getString("title")} | Total episode: ${episodes.length()}")

            val list = (0 until episodes.length()).map { i ->
                val ep = episodes.getJSONObject(i)
                val epNum = ep.getString("episode")
                val epName = ep.optString("name", "Episode $epNum")
                val epUrl = ep.getString("url")

                SEpisode.create().apply {
                    url = epUrl
                    name = epName
                    episode_number = epNum.toFloatOrNull() ?: (i + 1).toFloat()
                    date_upload = System.currentTimeMillis()
                }.also {
                    tracker.debug("Episode[$i]: $epName | url=$epUrl")
                }
            }.reversed()

            tracker.success("Berhasil parse ${list.size} episode")
            perf.end()
            list
        } catch (e: Exception) {
            tracker.error("getEpisodeList gagal: ${e.message}")
            perf.end()
            emptyList()
        }
    }

    // ============================ Video Links =============================

    override fun videoListRequest(episode: SEpisode): Request {
        val tracker = FeatureTracker("UP-VideoRequest")
        tracker.debug("Episode URL: ${episode.url}")
        // Kembalikan request dummy, logic ada di videoListParse
        return GET(episode.url, headers)
    }

    override fun videoListParse(response: Response): List<Video> {
        val perf = PerformanceTracker("UP-VideoListParse")
        val tracker = FeatureTracker("UP-VideoListParse")
        perf.start()
        tracker.start()

        val episodeUrl = response.request.url.toString()
        tracker.debug("Episode URL: $episodeUrl")

        val extractor = ExtractorFactory.get(episodeUrl, client)
        if (extractor == null) {
            tracker.error("Tidak ada extractor untuk domain: $episodeUrl")
            perf.end()
            return emptyList()
        }

        tracker.debug("Menggunakan extractor: ${extractor::class.simpleName}")

        return try {
            val videos = runBlocking {
                extractor.getVideoList(episodeUrl, client)
            }
            tracker.success("Berhasil dapat ${videos.size} video")
            perf.end()
            videos
        } catch (e: Exception) {
            tracker.error("getVideoList gagal: ${e.message}")
            perf.end()
            emptyList()
        }
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = popularAnimeRequest(page)
    override fun latestUpdatesParse(response: Response) = popularAnimeParse(response)

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_JSON_URL
            title = "GitHub Raw URL (JSON)"
            summary = "Masukkan URL raw JSON dari GitHub\nContoh: https://raw.githubusercontent.com/user/repo/main/list.json"
            setDefaultValue("")
            dialogTitle = "GitHub Raw URL"
            dialogMessage = "Format: https://raw.githubusercontent.com/..."
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_JSON_URL = "github_raw_url"
        private const val FALLBACK_URL = "" // Kosong, wajib isi di preferences
    }
}
