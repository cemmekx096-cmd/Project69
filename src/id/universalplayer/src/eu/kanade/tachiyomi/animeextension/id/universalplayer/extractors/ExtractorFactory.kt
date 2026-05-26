package eu.kanade.tachiyomi.animeextension.id.universalplayer.extractors

import okhttp3.OkHttpClient

object ExtractorFactory {
    private fun extractors(client: OkHttpClient) = listOf(
        VidvfExtractor(client),
        // Tambah extractor baru disini
    )

    fun get(url: String, client: OkHttpClient): AnimeExtractor? {
        val host = runCatching {
            java.net.URI(url).host ?: ""
        }.getOrDefault("")

        return extractors(client).find { extractor ->
            extractor.supportedDomains.any { host.contains(it) }
        }
    }
}
