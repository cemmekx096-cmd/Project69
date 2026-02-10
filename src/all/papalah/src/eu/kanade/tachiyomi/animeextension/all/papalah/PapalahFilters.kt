package eu.kanade.tachiyomi.animeextension.all.papalah

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

internal object PapalahFilters {

    // ========================== Tag Filter ================================
    class TagFilter(tags: Tags) : UriPartFilter(
        "Tag",
        tags,
    )

    // ========================== Sort Filter ===============================
    class SortFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Tag("Latest", ""),
            Tag("Most Viewed", "hot"),
        ),
    )

    // ======================== Base Filter Class ===========================
    open class UriPartFilter(displayName: String, private val options: Tags) :
        AnimeFilter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
        fun toUriPart() = options[state].second
        fun isEmpty() = options[state].second == ""
        fun isDefault() = state == 0
    }

    // ======================== Popular Tags ================================
    fun getPopularTags(): Tags {
        return arrayOf(
            Tag("<Select Tag>", ""),
            // Tambahkan beberapa tags populer sebagai fallback
            Tag("自慰", "自慰"),
            Tag("做爱", "做爱"),
            Tag("自拍", "自拍"),
            Tag("内射", "内射"),
            Tag("台湾", "台湾"),
            Tag("明星", "明星"),
            Tag("老师", "老师"),
            Tag("同事", "同事"),
        )
    }

    // ===================== Dynamic Tag Fetcher ============================
    fun fetchTagsFromPage(client: OkHttpClient, baseUrl: String): Tags {
        return try {
            val response = client.newCall(
                okhttp3.Request.Builder()
                    .url("$baseUrl/tag-list")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build(),
            ).execute()

            if (!response.isSuccessful) {
                android.util.Log.w("PapalahFilters", "Failed to fetch /tag-list: ${response.code}")
                return getPopularTags()
            }

            val document = Jsoup.parse(response.body.string())
            val tags = mutableSetOf<Tag>()
            tags.add(Tag("<Select Tag>", ""))

            // METHOD 1: Dari popular_keywords section
            // Pattern: <a href="./tag/自慰" title="自慰A片">...</a>
            document.select("div.popular_keywords a[href^='./tag/']").forEach { element ->
                extractTagFromElement(element, "./tag/", tags)
            }

            // METHOD 2: Dari popular_keywords dengan href yang tidak ada ./tag/
            // Pattern: <a href="./tag/自慰">...</a>
            document.select("div.popular_keywords a[href*='自慰'], div.popular_keywords a[href*='做爱']").forEach { element ->
                val href = element.attr("href")
                if (href.startsWith("./")) {
                    val tagName = href.removePrefix("./").removeSuffix("/")
                    if (tagName.isNotEmpty() && tagName != "tag") {
                        val displayName = element.text().trim().removeSurrounding("\"")
                        if (displayName.isNotEmpty()) {
                            tags.add(Tag(displayName, tagName))
                        }
                    }
                }
            }

            // METHOD 3: Dari tag-list dengan class tag-item
            // Pattern: <a href="./自慰" class="tag-item" title="自慰A片和成人影片">
            document.select("div.tag-list a.tag-item[href^='./']").forEach { element ->
                val href = element.attr("href")
                val tagName = href.removePrefix("./").removeSuffix("/")
                if (tagName.isNotEmpty()) {
                    val displayName = element.select("span").text().trim()
                        .ifEmpty { element.attr("title").substringBefore("A片").trim() }
                        .ifEmpty { tagName }

                    tags.add(Tag(displayName, tagName))
                }
            }

            // METHOD 4: Dari footer_keywords section
            // Pattern: <div class="footer_keywords"> ... <a href="/tag/自慰">自慰</a>
            document.select("div.footer_keywords a[href^='/tag/'], div.footer_keywords a[href^='./tag/']").forEach { element ->
                extractTagFromElement(element, "/tag/", tags)
            }

            // METHOD 5: Dari keywords row
            // Pattern: <div class="keywords row"> ... <a href="/自慰">自慰</a>
            document.select("div.keywords a[href^='/'], div.keywords a[href^='./']").forEach { element ->
                val href = element.attr("href")
                val tagName = when {
                    href.startsWith("/tag/") -> href.removePrefix("/tag/")
                    href.startsWith("./tag/") -> href.removePrefix("./tag/")
                    href.startsWith("/") -> href.removePrefix("/")
                    href.startsWith("./") -> href.removePrefix("./")
                    else -> href
                }.removeSuffix("/")

                if (tagName.isNotEmpty() && tagName != "tag") {
                    val displayName = element.text().trim()
                        .ifEmpty { element.attr("title").substringBefore("A片").trim() }
                        .ifEmpty { tagName }
                    
                    tags.add(Tag(displayName, tagName))
                }
            }

            // METHOD 6: Cari semua anchor tags dengan text Cina/karakter khusus
            document.select("a").forEach { element ->
                val href = element.attr("href")
                val text = element.text().trim()

                // Jika href mengandung karakter Cina dan text juga karakter Cina
                if (text.isNotEmpty() && text.matches(Regex(".*[\\p{Script=Han}].*"))) {
                    when {
                        href.startsWith("./tag/") -> extractTagFromElement(element, "./tag/", tags)
                        href.startsWith("/tag/") -> extractTagFromElement(element, "/tag/", tags)
                        href.startsWith("./") && !href.contains(".") -> {
                            // Pattern: ./自慰
                            val tagName = href.removePrefix("./").removeSuffix("/")
                            if (tagName.isNotEmpty()) {
                                tags.add(Tag(text, tagName))
                            }
                        }
                        href.startsWith("/") && !href.contains(".") && href.count { it == '/' } == 1 -> {
                            // Pattern: /自慰
                            val tagName = href.removePrefix("/").removeSuffix("/")
                            if (tagName.isNotEmpty()) {
                                tags.add(Tag(text, tagName))
                            }
                        }
                    }
                }
            }

            // Sort tags alphabetically by Chinese character
            val sortedTags = tags.sortedBy { it.first }
                .toMutableList()

            // Pastikan "<Select Tag>" ada di index 0
            if (sortedTags.isNotEmpty() && sortedTags[0].first != "<Select Tag>") {
                sortedTags.add(0, Tag("<Select Tag>", ""))
            }

            android.util.Log.d("PapalahFilters", "✅ Fetched ${sortedTags.size - 1} tags from /tag-list")
            return sortedTags.toTypedArray()
            
        } catch (e: Exception) {
            android.util.Log.e("PapalahFilters", "❌ Error fetching tags: ${e.message}")
            return getPopularTags()
        }
    }

    // Helper function untuk extract tag dari element dengan pattern tertentu
    private fun extractTagFromElement(element: org.jsoup.nodes.Element, prefix: String, tags: MutableSet<Tag>) {
        val href = element.attr("href")
        if (href.startsWith(prefix)) {
            val tagName = href.removePrefix(prefix).removeSuffix("/")
            if (tagName.isNotEmpty()) {
                val displayName = element.text().trim()
                    .removeSurrounding("\"")
                    .ifEmpty { element.attr("title").substringBefore("A片").trim() }
                    .ifEmpty { tagName }

                if (displayName.isNotEmpty()) {
                    tags.add(Tag(displayName, tagName))
                }
            }
        }
    }
}

typealias Tags = Array<Tag>
typealias Tag = Pair<String, String>
