package eu.kanade.tachiyomi.lib.lk21extractor

object Lk21Common {
    
    // Standard user agent untuk LK21
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    
    /**
     * Clean title dari prefix/suffix yang tidak diinginkan
     */
    fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""\(?\d{4}\)?"""), "") // Remove year
            .replace(Regex("""(?i)(BluRay|WEB-DL|HDRip|HDCAM|CAM)"""), "") // Remove quality tags
            .replace(Regex("""(?i)(subtitle indonesia|sub indo|nonton|film)"""), "") // Remove common prefixes
            .trim()
            .replace(Regex("""\s+"""), " ") // Normalize spaces
    }
    
    /**
     * Extract year dari title atau description
     */
    fun extractYear(text: String): String? {
        val yearRegex = Regex("""\b(19|20)\d{2}\b""")
        return yearRegex.find(text)?.value
    }
    
    /**
     * Parse quality dari text (untuk video title)
     */
    fun parseQuality(text: String): String {
        return when {
            text.contains("1080", ignoreCase = true) -> "1080p"
            text.contains("720", ignoreCase = true) -> "720p"
            text.contains("480", ignoreCase = true) -> "480p"
            text.contains("360", ignoreCase = true) -> "360p"
            text.contains("BluRay", ignoreCase = true) -> "BluRay"
            text.contains("WEB-DL", ignoreCase = true) -> "WEB-DL"
            text.contains("HDRip", ignoreCase = true) -> "HDRip"
            else -> "Unknown"
        }
    }
    
    /**
     * Detect if URL is for series/drama
     */
    fun isSeriesUrl(url: String): Boolean {
        return url.contains("nontondrama", ignoreCase = true) ||
               url.contains("/series/", ignoreCase = true) ||
               url.contains("/drama/", ignoreCase = true)
    }
    
    /**
     * Detect if content is movie or series based on HTML
     */
    fun isMovie(html: String): Boolean {
        // Series biasanya punya episode list atau redirect ke nontondrama
        val hasEpisodeList = html.contains("list-episode", ignoreCase = true) ||
                            html.contains("episode-list", ignoreCase = true)
        val hasRedirect = html.contains("nontondrama", ignoreCase = true)
        
        return !hasEpisodeList && !hasRedirect
    }
    
    /**
     * Extract IMDb rating jika tersedia
     */
    fun extractRating(text: String): String? {
        val ratingRegex = Regex("""(\d+\.?\d*)/10""")
        return ratingRegex.find(text)?.groupValues?.get(1)
    }
    
    /**
     * Normalize genre string
     */
    fun normalizeGenres(genres: String): String {
        return genres
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
    }
}
