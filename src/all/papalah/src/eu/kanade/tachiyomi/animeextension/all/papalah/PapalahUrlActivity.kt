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
        val pathSegments = intent?.data?.pathSegments

        if (pathSegments != null && pathSegments.size > 1) {
            val item = pathSegments[1]
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.ANIMESEARCH"
                
                when (pathSegments[0]) {
                    "v" -> {
                        // Video detail page
                        // URL format: /v/{id}/{title}
                        putExtra("query", "${Papalah().name}:${intent?.data?.path}")
                        putExtra("filter", packageName)
                    }
                    "tag" -> {
                        // Tag filter page
                        // URL format: /tag/{tag-name}
                        putExtra("query", "${Papalah().name}:tag:$item")
                        putExtra("filter", packageName)
                    }
                }
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(tag, "Could not start activity", e)
            }
        } else {
            Log.e(tag, "Could not parse URI from intent: $intent")
        }

        finish()
        exitProcess(0)
    }
}
