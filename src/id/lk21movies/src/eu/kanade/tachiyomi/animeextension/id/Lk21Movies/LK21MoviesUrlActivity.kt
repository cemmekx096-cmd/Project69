package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlin.system.exitProcess

/**
 * Activity untuk handle deep links dari browser
 * Ketika user klik link LK21 di browser, akan langsung dibuka di Aniyomi
 */
class LK21MoviesUrlActivity : Activity() {

    private val TAG = "LK21MoviesUrlActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pathSegments = intent?.data?.pathSegments
        if (pathSegments != null && pathSegments.size > 0) {
            val mainIntent = Intent().apply {
                action = "eu.kanade.tachiyomi.ANIMESEARCH"
                putExtra("query", "${LK21Movies.PREFIX_SEARCH}${intent?.data?.toString()}")
                putExtra("filter", packageName)
            }

            try {
                startActivity(mainIntent)
            } catch (e: ActivityNotFoundException) {
                Log.e(TAG, "Could not start activity", e)
            }
        } else {
            Log.e(TAG, "Could not parse URI from intent $intent")
        }

        finish()
        exitProcess(0)
    }

    companion object {
        private const val PREFIX_SEARCH = "id:"
    }
}
