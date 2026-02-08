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
 * Mendukung:
 * - https://papalah.com/v/{id}/{title}
 * - https://www.papalah.com/v/{id}/{title}
 * - https://papalah.com/tag/{tag}
 * - https://www.papalah.com/tag/{tag}
 */
class PapalahUrlActivity : Activity() {

    private val tag = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uri = intent?.data
        if (uri == null) {
            Log.e(tag, "No URI in intent")
            finish()
            return
        }

        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) {
            Log.e(tag, "Empty path segments")
            finish()
            return
        }

        val query = when (pathSegments[0]) {
            "v" -> {
                // Untuk video: gunakan full URL
                // Input: https://papalah.com/v/104576/title
                // Output: Papalah:https://papalah.com/v/104576/title
                "${Papalah().name}:${uri.toString()}"
            }
            "tag" -> {
                // Untuk tag: gunakan nama tag saja
                // Input: https://papalah.com/tag/action
                // Output: Papalah:tag:action
                val tagName = pathSegments.getOrNull(1) ?: run {
                    Log.e(tag, "Missing tag name")
                    finish()
                    return
                }
                "${Papalah().name}:tag:$tagName"
            }
            else -> {
                Log.e(tag, "Unknown path type: ${pathSegments[0]}")
                finish()
                return
            }
        }

        Log.d(tag, "Generated query: $query")

        val mainIntent = Intent().apply {
            action = "eu.kanade.tachiyomi.ANIMESEARCH"
            putExtra("query", query)
            putExtra("filter", packageName)
        }

        try {
            startActivity(mainIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(tag, "Could not start activity", e)
        }

        finish()
    }
}
