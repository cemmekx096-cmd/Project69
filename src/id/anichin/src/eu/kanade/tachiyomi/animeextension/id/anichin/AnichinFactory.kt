package eu.kanade.tachiyomi.animeextension.id.anichin

import org.jsoup.Jsoup

object AnichinFactory {

    /**
     * If input is HTML containing an <iframe>, extract iframe.src
     * If input is a plain URL, return it.
     * If input contains base64 or encoded iframe HTML, caller should decode beforehand.
     */
    fun extractIframeSrcOrUrl(input: String?): String? {
        if (input.isNullOrEmpty()) return null

        // Quick detect if this is an iframe HTML string
        val maybe = input.trim()
        return if (maybe.contains("<iframe", ignoreCase = true) || maybe.contains("src=\"", ignoreCase = true)) {
            try {
                val doc = Jsoup.parse(maybe)
                val iframe = doc.selectFirst("iframe")
                val src = iframe?.attr("src")?.trim()
                when {
                    src == null || src.isEmpty() -> {
                        // try data-src or inner src
                        iframe?.attr("data-src")?.takeIf { it.isNotEmpty() }
                    }
                    src.startsWith("//") -> "https:$src"
                    else -> src
                }
            } catch (e: Exception) {
                // Not parseable HTML — return original as fallback
                input
            }
        } else {
            // plain URL or encoded URL
            input
        }
    }
}
