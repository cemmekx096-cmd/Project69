package eu.kanade.tachiyomi.animeextension.id.universalplayer.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.OkHttpClient

interface AnimeExtractor {
    val supportedDomains: List<String>
    suspend fun getVideoList(url: String, client: OkHttpClient): List<Video>
}
