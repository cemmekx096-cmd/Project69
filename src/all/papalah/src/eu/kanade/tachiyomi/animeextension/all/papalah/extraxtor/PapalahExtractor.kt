package eu.kanade.tachiyomi.animeextension.all.papalah.extractors

import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.OkHttpClient

class PapalahExtractor(private val client: OkHttpClient, private val headers: Headers) {

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    // ======================== Streamtape =================================

    internal fun streamtapeExtractor(url: String, prefix: String): List<Video> {
        return try {
            val response = client.newCall(GET(url, headers)).execute()
            val html = response.body.string()

            // Streamtape pattern: document.getElementById('ideoo').innerHTML = "...videolink...";
            val videoUrl = Regex("""getElementById\(['"]ideoo['"]\)\.innerHTML = ["'](.+?)["']""")
                .find(html)?.groupValues?.get(1)?.let { partial ->
                    "https:$partial"
                }

            if (videoUrl != null) {
                listOf(Video(videoUrl, "${prefix}Streamtape", videoUrl, headers))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ======================== Doodstream =================================

    internal fun doodExtractor(url: String, prefix: String): List<Video> {
        return try {
            val response = client.newCall(GET(url, headers)).execute()
            val html = response.body.string()

            val token = Regex("""/pass_md5/([^']+)""")
                .find(html)?.groupValues?.get(1)

            if (token != null) {
                val videoUrl = "https://dood.li$token"
                listOf(Video(videoUrl, "${prefix}Doodstream", videoUrl, headers))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ======================== Mixdrop ====================================

    internal fun mixdropExtractor(url: String, prefix: String): List<Video> {
        return try {
            val response = client.newCall(GET(url, headers)).execute()
            val html = response.body.string()

            val videoUrl = Regex("""MDCore\.wurl\s*=\s*["']([^"']+)["']""")
                .find(html)?.groupValues?.get(1)
                ?.let { "https:$it" }

            if (videoUrl != null) {
                listOf(Video(videoUrl, "${prefix}Mixdrop", videoUrl, headers))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ======================== Fembed =====================================

    internal fun fembedExtractor(url: String, prefix: String): List<Video> {
        return try {
            val id = url.substringAfterLast("/").substringBefore("?")
            val apiUrl = url.substringBefore("/v/") + "/api/source/$id"

            val response = client.newCall(
                GET(apiUrl, headers.newBuilder()
                    .add("X-Requested-With", "XMLHttpRequest")
                    .build())
            ).execute()

            val json = response.body.string()
            val videos = mutableListOf<Video>()

            Regex(""""file":"([^"]+)","label":"([^"]+)"""").findAll(json).forEach { match ->
                val videoUrl = match.groupValues[1].replace("\\/", "/")
                val quality = match.groupValues[2]
                videos.add(Video(videoUrl, "$prefix$quality", videoUrl, headers))
            }

            videos
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ======================== Upstream ===================================

    internal fun upstreamExtractor(url: String, prefix: String): List<Video> {
        return try {
            val response = client.newCall(GET(url, headers)).execute()
            val html = response.body.string()

            // Upstream biasanya punya sources array
            val videos = mutableListOf<Video>()

            Regex(""""file":"([^"]+)","label":"([^"]+)"""").findAll(html).forEach { match ->
                val videoUrl = match.groupValues[1]
                val quality = match.groupValues[2]
                videos.add(Video(videoUrl, "$prefix$quality", videoUrl, headers))
            }

            videos
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ======================== StreamWish =================================

    internal fun streamwishExtractor(url: String, prefix: String): List<Video> {
        return try {
            val response = client.newCall(GET(url, headers)).execute()
            val html = response.body.string()

            // StreamWish/StrWish pattern
            val videoUrl = Regex("""sources:\s*\[\{file:"([^"]+)"""")
                .find(html)?.groupValues?.get(1)

            if (videoUrl != null) {
                listOf(Video(videoUrl, "${prefix}StreamWish", videoUrl, headers))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ======================== FileMoon ===================================

    internal fun filemoonExtractor(url: String, prefix: String): List<Video> {
        return try {
            val response = client.newCall(GET(url, headers)).execute()
            val html = response.body.string()

            // FileMoon pattern
            val packed = Regex("""eval\(function\(p,a,c,k,e,d\).*?\}\(.*?\)\)""", RegexOption.DOT_MATCHES_ALL)
                .find(html)?.value

            val unpacked = packed?.let { unpackJs(it) }

            unpacked?.let {
                Regex("""sources:\[\{file:"([^"]+)"""").find(it)?.groupValues?.get(1)
            }?.let { videoUrl ->
                listOf(Video(videoUrl, "${prefix}FileMoon", videoUrl, headers))
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ======================== VidGuard ===================================

    internal fun vidguardExtractor(url: String, prefix: String): List<Video> {
        return try {
            val response = client.newCall(GET(url, headers)).execute()
            val html = response.body.string()

            // VidGuard pattern
            val videoUrl = Regex("""sources:\s*\[\{src:"([^"]+)"""")
                .find(html)?.groupValues?.get(1)

            if (videoUrl != null) {
                listOf(Video(videoUrl, "${prefix}VidGuard", videoUrl, headers))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ======================== M3U8 =======================================

    internal fun m3u8Extractor(url: String, prefix: String): List<Video> {
        return try {
            playlistUtils.extractFromHls(
                playlistUrl = url,
                videoNameGen = { quality -> "${prefix}HLS - $quality" },
                referer = headers["Referer"] ?: ""
            )
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ======================== JsUnpacker =================================

    fun unpackJs(packedJs: String): String? {
        return try {
            val regex = Regex("""eval\(function\(p,a,c,k,e,d\).*?}\((.*?)\)\)""", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(packedJs) ?: return null

            val args = match.groupValues[1]
                .split("',")
                .map { it.trim().removeSurrounding("'", "'") }

            if (args.size < 6) return null

            val payload = args[0]
            val radix = args[1].toIntOrNull() ?: 36
            val count = args[2].toIntOrNull() ?: 0
            val symtab = args[3].split("|")

            unpack(payload, radix, count, symtab)
        } catch (e: Exception) {
            null
        }
    }

    private fun unpack(payload: String, radix: Int, count: Int, symtab: List<String>): String {
        var result = payload

        for (i in count - 1 downTo 0) {
            val replacement = symtab.getOrNull(i) ?: continue
            if (replacement.isNotEmpty()) {
                val pattern = "\\b${i.toString(radix)}\\b"
                result = result.replace(Regex(pattern), replacement)
            }
        }

        return result
    }
}
