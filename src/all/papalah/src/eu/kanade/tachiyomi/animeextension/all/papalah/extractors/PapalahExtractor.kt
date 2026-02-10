package eu.kanade.tachiyomi.animeextension.all.papalah.extractors

import okhttp3.Headers
import okhttp3.OkHttpClient

class PapalahExtractor(private val client: OkHttpClient, private val headers: Headers) {

    // Extractor ini SUDAH TIDAK DIPAKAI
    // Semua extraction logic ada di PapalahExtractorFactory.extractFromHtml()
    // File ini tetap ada untuk backward compatibility
}
