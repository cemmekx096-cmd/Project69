package eu.kanade.tachiyomi.lib.lk21extractor

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Domain fetcher untuk LK21
 * Fetch real domain dari landing page d21.team yang sering berubah
 */
object Lk21DomainFetcher {
    
    private const val TAG = "Lk21DomainFetcher"
    private const val LANDING_PAGE = "https://d21.team/"
    
    /**
     * Fetch real LK21 domain dari landing page
     * Flow: d21.team → lk21.de → tv8.lk21official.cc (final)
     */
    fun fetchRealDomain(client: OkHttpClient): String? {
        return try {
            Log.d(TAG, "Fetching landing page: $LANDING_PAGE")
            
            // Step 1: Fetch landing page
            val landingRequest = Request.Builder()
                .url(LANDING_PAGE)
                .header("User-Agent", Lk21Common.USER_AGENT)
                .build()
            
            val landingResponse = client.newCall(landingRequest).execute()
            val landingHtml = landingResponse.body.string()
            
            // Step 2: Parse button link (https://lk21.de)
            val doc = Jsoup.parse(landingHtml)
            val lk21Link = doc.selectFirst("a.cta-button.green-button")?.attr("href")
            
            if (lk21Link.isNullOrEmpty()) {
                Log.e(TAG, "Failed to find LK21 link in landing page")
                return null
            }
            
            Log.d(TAG, "Found LK21 link: $lk21Link")
            
            // Step 3: Follow redirect to get final domain
            val redirectRequest = Request.Builder()
                .url(lk21Link)
                .header("User-Agent", Lk21Common.USER_AGENT)
                .build()
            
            val redirectResponse = client.newCall(redirectRequest).execute()
            val finalUrl = redirectResponse.request.url.toString()
            
            // Extract base domain (e.g., https://tv8.lk21official.cc)
            val baseUrl = "https://${redirectResponse.request.url.host}"
            
            Log.d(TAG, "Final domain: $baseUrl")
            
            redirectResponse.close()
            landingResponse.close()
            
            baseUrl
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching domain: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Fetch domain dengan fallback
     */
    fun fetchDomainWithFallback(
        client: OkHttpClient,
        fallbackDomain: String = "https://tv8.lk21official.cc",
    ): String {
        val domain = fetchRealDomain(client)
        
        return if (domain != null) {
            Log.d(TAG, "Successfully fetched domain: $domain")
            domain
        } else {
            Log.w(TAG, "Failed to fetch domain, using fallback: $fallbackDomain")
            fallbackDomain
        }
    }
    
    /**
     * Check if domain is still valid
     */
    fun isDomainValid(client: OkHttpClient, domain: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(domain)
                .header("User-Agent", Lk21Common.USER_AGENT)
                .build()
            
            val response = client.newCall(request).execute()
            val isValid = response.isSuccessful
            
            response.close()
            
            Log.d(TAG, "Domain $domain is ${if (isValid) "valid" else "invalid"}")
            isValid
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking domain: ${e.message}")
            false
        }
    }
}
