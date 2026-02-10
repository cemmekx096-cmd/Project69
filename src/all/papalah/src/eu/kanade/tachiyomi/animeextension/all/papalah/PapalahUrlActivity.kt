package eu.kanade.tachiyomi.animeextension.all.papalah

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Deep link handler untuk Papalah URLs
 *
 * Format URL yang didukung:
 * 
 * 1. VIDEO DETAIL:
 * - https://papalah.com/v/{id}/{title}/
 * - https://www.papalah.com/v/{id}/{title}/
 * - https://papalah.com/v/{id}/
 * - https://www.papalah.com/v/{id}/
 * 
 * 2. TAG/BROWSE:
 * - https://papalah.com/tag/{tag-name}/
 * - https://www.papalah.com/tag/{tag-name}/
 * - https://papalah.com/tag/{tag-name}/page/{page}/
 * 
 * 3. TRENDING/HOT:
 * - https://papalah.com/hot/
 * - https://www.papalah.com/hot/
 * - https://papalah.com/hot/page/{page}/
 * 
 * 4. HOME/LATEST:
 * - https://papalah.com/
 * - https://www.papalah.com/
 * - https://papalah.com/page/{page}/
 * 
 * 5. DIRECT TAG LINKS (tanpa /tag/):
 * - https://papalah.com/{tag-name}/
 * - https://www.papalah.com/{tag-name}/
 */
class PapalahUrlActivity : Activity() {

    private val tag = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri == null) {
            Log.e(tag, "No URI found in intent")
            finishAndExit()
            return
        }

        Log.d(tag, "Processing URI: $uri")

        val host = uri.host ?: ""
        val pathSegments = uri.pathSegments ?: emptyList()

        // Pastikan kita hanya memproses domain Papalah
        if (!host.contains("papalah.com") && !host.contains("aalah.me")) {
            Log.e(tag, "Not a Papalah domain: $host")
            finishAndExit()
            return
        }

        try {
            val query = when {
                // HOME PAGE - latest videos
                pathSegments.isEmpty() -> {
                    "Papalah"
                }

                // VIDEO DETAIL: /v/{id}/{title}/
                pathSegments.size >= 2 && pathSegments[0] == "v" -> {
                    val videoId = pathSegments[1]
                    "Papalah:${uri.path}"
                }

                // SINGLE VIDEO: /v/{id}/
                pathSegments.size == 2 && pathSegments[0] == "v" -> {
                    val videoId = pathSegments[1]
                    "Papalah:/v/$videoId/"
                }

                // TAG PAGE: /tag/{tag-name}/
                pathSegments.size >= 2 && pathSegments[0] == "tag" -> {
                    val tagName = pathSegments[1]
                    "Papalah:tag:$tagName"
                }

                // HOT/TRENDING: /hot/
                pathSegments.size >= 1 && pathSegments[0] == "hot" -> {
                    val page = if (pathSegments.size >= 3 && pathSegments[1] == "page") {
                        pathSegments[2].toIntOrNull() ?: 1
                    } else {
                        1
                    }
                    "Papalah:hot:$page"
                }

                // PAGINATED HOME: /page/{page}/
                pathSegments.size == 2 && pathSegments[0] == "page" -> {
                    val page = pathSegments[1].toIntOrNull() ?: 1
                    "Papalah:latest:$page"
                }

                // DIRECT TAG (without /tag/): /{tag-name}/
                // Contoh: /自慰/ dari <a href="./自慰" class="tag-item">
                pathSegments.size == 1 -> {
                    val possibleTag = pathSegments[0]
                    // Cek jika ini bukan common paths
                    if (possibleTag !in listOf("about", "contact", "privacy", "terms", "dmca")) {
                        "Papalah:tag:$possibleTag"
                    } else {
                        null
                    }
                }

                // UNSUPPORTED URL
                else -> {
                    Log.w(tag, "Unsupported URL path: ${uri.path}")
                    null
                }
            }

            if (query != null) {
                val mainIntent = Intent().apply {
                    action = "eu.kanade.tachiyomi.ANIMESEARCH"
                    putExtra("query", query)
                    putExtra("filter", packageName)
                }

                try {
                    startActivity(mainIntent)
                } catch (e: ActivityNotFoundException) {
                    Log.e(tag, "Tachiyomi not found or doesn't support ANIMESEARCH action", e)
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "Error processing URL", e)
        }

        finishAndExit()
    }

    private fun finishAndExit() {
        finish()
        exitProcess(0)
    }
}
