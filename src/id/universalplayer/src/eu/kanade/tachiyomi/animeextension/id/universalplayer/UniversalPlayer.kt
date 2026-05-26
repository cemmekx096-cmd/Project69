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

    // ============================== Core ===============================

    private fun animeListRequest(): Request {
        val tracker = FeatureTracker("UP-Core")
        val url = githubRawUrl.ifBlank {
            tracker.warn("GitHub Raw URL kosong!")
            FALLBACK_URL
        }
        tracker.debug("Fetching JSON dari: $url")
        return GET(url, headers)
    }

    private fun animeListParse(response: Response): AnimesPage {
        val perf = PerformanceTracker("UP-Core")
        val tracker = FeatureTracker("UP-Core")
        perf.start()

        return try {
            val arr = JSONArray(response.body.string())
            tracker.debug("Total anime: ${arr.length()}")

            val animes = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SAnime.create().apply {
                    url = i.toString()
                    title = obj.getString("title")
                    thumbnail_url = obj.optString("poster", "").ifBlank { null }
                    status = SAnime.UNKNOWN
                }.also {
                    tracker.debug("Parsed[$i]: ${it.title}")
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

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int) = animeListRequest()
    override fun popularAnimeParse(response: Response) = animeListParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) = animeListRequest()
    override fun searchAnimeParse(response: Response) = animeListParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = animeListRequest()
    override fun latestUpdatesParse(response: Response) = animeListParse(response)

    // =============================== Filters ==============================

    override fun getFilterList() = AnimeFilterList()

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        val tracker = FeatureTracker("UP-Details")
        tracker.debug("Request detail index: ${anime.url}")
        return animeListRequest()
    }

    override fun animeDetailsParse(response: Response): SAnime {
        return SAnime.create()
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime) = animeListRequest()

    override fun episodeListParse(response: Response): List<SEpisode> = emptyList()

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val perf = PerformanceTracker("UP-Episodes")
        val tracker = FeatureTracker("UP-Episodes")
        perf.start()
        tracker.start()

        return try {
            val response = client.newCall(animeListRequest()).execute()
            val arr = JSONArray(response.body.string())

            val index = anime.url.toIntOrNull()
            if (index == null) {
                tracker.error("Index tidak valid: ${anime.url}")
                perf.end()
                return emptyList()
            }

            val animeObj = arr.getJSONObject(index)
            val episodes = animeObj.getJSONArray("episodes")
            tracker.debug("Anime: ${animeObj.getString("title")} | Total: ${episodes.length()}")

            val list = mutableListOf<SEpisode>()
            for (i in 0 until episodes.length()) {
                val ep = episodes.getJSONObject(i)
                val epNum = ep.getString("episode")
                val epName = ep.optString("name", "Episode $epNum")
                val epUrl = ep.getString("url")

                val episode = SEpisode.create()
                episode.url = epUrl
                episode.name = epName
                episode.episode_number = epNum.toFloatOrNull() ?: (i + 1).toFloat()
                episode.date_upload = System.currentTimeMillis()

                tracker.debug("Episode[$i]: $epName | url=$epUrl")
                list.add(episode)
            }

            val result = list.reversed()
            tracker.success("Berhasil parse ${result.size} episode")
            perf.end()
            result
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
            tracker.error("Tidak ada extractor untuk: $episodeUrl")
            perf.end()
            return emptyList()
        }

        tracker.debug("Extractor: ${extractor::class.simpleName}")

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
        private const val FALLBACK_URL = ""
    }
}
