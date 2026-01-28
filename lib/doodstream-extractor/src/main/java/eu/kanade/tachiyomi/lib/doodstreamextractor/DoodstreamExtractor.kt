package eu.kanade.tachiyomi.lib.doodstreamextractor

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HEAD
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

class DoodstreamExtractor(private val client: OkHttpClient) {
    
    private val headers: Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        .add("Accept-Language", "en-US,en;q=0.5")
        .add("Accept-Encoding", "gzip, deflate, br")
        .add("DNT", "1")
        .add("Connection", "keep-alive")
        .add("Upgrade-Insecure-Requests", "1")
        .add("Sec-Fetch-Dest", "document")
        .add("Sec-Fetch-Mode", "navigate")
        .add("Sec-Fetch-Site", "none")
        .add("Sec-Fetch-User", "?1")
        .add("TE", "trailers")
        .build()
    
    fun videosFromUrl(url: String, prefix: String = ""): List<Video> {
        android.util.Log.d("DoodstreamExtractor", "=== EXTRACT START ===")
        android.util.Log.d("DoodstreamExtractor", "URL: $url")
        
        return try {
            val videoId = extractVideoId(url)
            if (videoId.isEmpty()) {
                android.util.Log.w("DoodstreamExtractor", "No video ID found")
                return emptyList()
            }
            
            android.util.Log.d("DoodstreamExtractor", "Video ID: $videoId")
            
            // Strategy 1: Try known CDN patterns first (fastest)
            val cdnVideos = tryKnownCdnPatterns(videoId, prefix)
            if (cdnVideos.isNotEmpty()) {
                return cdnVideos
            }
            
            // Strategy 2: Try embed page extraction
            val embedVideos = tryEmbedPageExtraction(url, videoId, prefix)
            if (embedVideos.isNotEmpty()) {
                return embedVideos
            }
            
            // Strategy 3: Fallback to direct URL
            android.util.Log.w("DoodstreamExtractor", "All strategies failed, using fallback")
            listOf(Video(url, "${prefix}Doodstream (Direct)", url))
            
        } catch (e: Exception) {
            android.util.Log.e("DoodstreamExtractor", "Extraction failed", e)
            emptyList()
        } finally {
            android.util.Log.d("DoodstreamExtractor", "=== EXTRACT END ===")
        }
    }
    
    private fun extractVideoId(url: String): String {
        val patterns = listOf(
            Regex("""/(d|e|v)/([a-zA-Z0-9]{10,})"""),
            Regex("""\?v=([a-zA-Z0-9]{10,})"""),
            Regex("""&id=([a-zA-Z0-9]{10,})"""),
            Regex("""video/([a-zA-Z0-9]{10,})""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(url)
            if (match != null) {
                return match.groupValues.last()
            }
        }
        
        // Last resort: get last part of URL
        return url.substringAfterLast("/").substringBefore("?").substringBefore("&")
    }
    
    private fun tryKnownCdnPatterns(videoId: String, prefix: String): List<Video> {
        val cdnDomains = listOf(
            "dsvplay.com",
            "myvidplay.com", 
            "doodstream.com",
            "dood.la",
            "dood.watch",
            "dood.to"
        )
        
        val patterns = listOf(
            "/v/{id}.mp4",
            "/d/{id}.mp4", 
            "/e/{id}.mp4",
            "/{id}.mp4",
            "/v/{id}/video.mp4",
            "/{id}/video.mp4",
            "/v/{id}/playlist.m3u8",
            "/{id}/playlist.m3u8"
        )
        
        val testUrls = mutableListOf<String>()
        
        for (domain in cdnDomains) {
            for (pattern in patterns) {
                testUrls.add("https://$domain${pattern.replace("{id}", videoId)}")
            }
        }
        
        // Also try CDN subdomain patterns
        testUrls.addAll(listOf(
            "https://$videoId.doodcdn.com/$videoId.mp4",
            "https://$videoId.cdndownload.com/$videoId.mp4",
            "https://$videoId.clouddatacdn.com/$videoId.mp4"
        ))
        
        android.util.Log.d("DoodstreamExtractor", "Testing ${testUrls.size} CDN URLs")
        
        for (testUrl in testUrls) {
            try {
                val response = client.newCall(HEAD(testUrl, headers)).execute()
                
                val contentType = response.header("Content-Type", "").lowercase(Locale.US)
                val contentLength = response.header("Content-Length", "0").toLong()
                
                android.util.Log.d("DoodstreamExtractor", "Test $testUrl → ${response.code}, $contentType, ${contentLength}bytes")
                
                if (response.isSuccessful && 
                    (contentType.contains("video/") || 
                     contentType.contains("application/x-mpegurl") ||
                     contentType.contains("application/vnd.apple.mpegurl")) &&
                    contentLength > 1024) {
                    
                    android.util.Log.d("DoodstreamExtractor", "✓ Found valid CDN URL: $testUrl")
                    return listOf(Video(testUrl, "${prefix}Doodstream CDN", testUrl))
                }
                
                response.close()
            } catch (e: Exception) {
                // Continue testing
            }
        }
        
        return emptyList()
    }
    
    private fun tryEmbedPageExtraction(url: String, videoId: String, prefix: String): List<Video> {
        val embedUrl = if (url.contains("/d/")) {
            url.replace("/d/", "/e/")
        } else {
            url
        }
        
        android.util.Log.d("DoodstreamExtractor", "Trying embed page: $embedUrl")
        
        return try {
            val response = client.newCall(GET(embedUrl, headers)).execute()
            val html = response.body?.string() ?: ""
            
            // Look for direct video URLs in HTML
            val videoUrls = mutableListOf<Video>()
            
            // Pattern 1: Direct MP4/M3U8 links
            val directPattern = Regex("""(https?://[^\s"']*\.(?:mp4|m3u8|mkv|webm)[^\s"']*)""")
            directPattern.findAll(html).forEach { match ->
                val foundUrl = match.groupValues[1]
                if (foundUrl.contains(videoId) || foundUrl.contains("dood")) {
                    videoUrls.add(Video(foundUrl, "${prefix}Doodstream Direct", foundUrl))
                }
            }
            
            // Pattern 2: Sources in scripts
            val scriptPatterns = listOf(
                Regex("""sources\s*:\s*\[\s*\{\s*src\s*:\s*["']([^"']+)["']"""),
                Regex("""file\s*:\s*["']([^"']+)["']"""),
                Regex(""""url"\s*:\s*["']([^"']+)["']"""),
                Regex("""video_url\s*:\s*["']([^"']+)["']""")
            )
            
            for (pattern in scriptPatterns) {
                pattern.findAll(html).forEach { match ->
                    val foundUrl = match.groupValues[1]
                    videoUrls.add(Video(foundUrl, "${prefix}Doodstream Script", foundUrl))
                }
            }
            
            // Pattern 3: Iframe sources
            val iframePattern = Regex("""<iframe[^>]+src=["']([^"']+)["']""")
            iframePattern.findAll(html).forEach { match ->
                val iframeUrl = match.groupValues[1]
                if (iframeUrl.contains(".mp4") || iframeUrl.contains(".m3u8")) {
                    videoUrls.add(Video(iframeUrl, "${prefix}Doodstream Iframe", iframeUrl))
                }
            }
            
            // Remove duplicates
            videoUrls.distinctBy { it.videoUrl }
            
        } catch (e: Exception) {
            android.util.Log.e("DoodstreamExtractor", "Embed page failed", e)
            emptyList()
        }
    }
}

// Helper extension for HEAD request
private fun HEAD(url: String, headers: Headers): Request {
    return Request.Builder()
        .url(url)
        .headers(headers)
        .head()
        .build()
}
