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
            Tag("巨乳 (Big Breasts)", "巨乳"),
            Tag("偷情 (Affair)", "偷情"),
            Tag("臺灣 (Taiwan)", "臺灣"),
            Tag("做愛 (Sex)", "做愛"),
            Tag("騷話 (Dirty Talk)", "騷話"),
            Tag("淫語 (Lewd Talk)", "淫語"),
            Tag("騎乘 (Riding)", "騎乘"),
            Tag("老板娘 (Boss Lady)", "老板娘"),
            Tag("尿尿 (Peeing)", "尿尿"),
            Tag("長腿 (Long Legs)", "長腿"),
            Tag("男友 (Boyfriend)", "男友"),
            Tag("大二 (Sophomore)", "大二"),
            Tag("同學 (Classmate)", "同學"),
            Tag("女大 (College Girl)", "女大"),
            Tag("情侶 (Couple)", "情侶"),
            Tag("無套 (No Condom)", "無套"),
            Tag("18", "18"),
            Tag("屁眼 (Anus)", "屁眼"),
            Tag("新加坡 (Singapore)", "新加坡"),
            Tag("處女 (Virgin)", "處女"),
            Tag("口爆 (Oral Creampie)", "口爆"),
            Tag("06", "06"),
            Tag("大一 (Freshman)", "大一"),
            Tag("李宗 (Li Zong)", "李宗"),
            Tag("女友 (Girlfriend)", "女友"),
            Tag("母家 (Mother's Home)", "母家"),
            Tag("字幕 (Subtitle)", "字幕"),
            Tag("4p", "4p"),
            Tag("露臉 (Face Shown)", "露臉"),
            Tag("健身 (Fitness)", "健身"),
            Tag("白漿 (White Fluid)", "白漿"),
            Tag("KTV", "KTV"),
            Tag("按摩 (Massage)", "按摩"),
            Tag("雜色 (Mixed)", "雜色"),
            Tag("出血 (Bleeding)", "出血"),
            Tag("簡介 (Introduction)", "簡介"),
            Tag("成都 (Chengdu)", "成都"),
            Tag("少婦 (Young Woman)", "少婦"),
            Tag("小美 (Xiao Mei)", "小美"),
            Tag("母狗 (Female Dog)", "母狗"),
        )
    }

    // ===================== Dynamic Tag Fetcher ============================

    fun fetchTagsFromPage(client: OkHttpClient, baseUrl: String): Tags {
        return try {
            val response = client.newCall(
                okhttp3.Request.Builder()
                    .url("$baseUrl/tag-list") // ← Changed: Fetch from /tag-list page
                    .build(),
            ).execute()

            val document = Jsoup.parse(response.body.string())

            val tags = mutableSetOf<Tag>()
            tags.add(Tag("<Select Tag>", ""))

            // Method 1: Dari popular_keywords section
            document.select("div.popular_keywords a[href*=/tag/]").forEach { element ->
                val tagName = element.text().trim()
                val tagValue = element.attr("href")
                    .substringAfter("/tag/")
                    .substringBefore("/")
                    .trim()

                if (tagName.isNotEmpty() && tagValue.isNotEmpty()) {
                    tags.add(Tag(tagName, tagValue))
                }
            }

            // Method 2: Dari tag-list dengan class tag-item
            document.select("div.tag-list a.tag-item[href*=/tag/]").forEach { element ->
                val tagName = element.text().trim()
                val tagValue = element.attr("href")
                    .substringAfter("/tag/")
                    .substringBefore("/")
                    .trim()

                if (tagName.isNotEmpty() && tagValue.isNotEmpty()) {
                    tags.add(Tag(tagName, tagValue))
                }
            }

            // Fallback ke popular tags jika gagal fetch
            if (tags.size <= 1) {
                android.util.Log.w("PapalahFilters", "No tags found from /tag-list, using popular tags")
                getPopularTags()
            } else {
                android.util.Log.d("PapalahFilters", "Fetched ${tags.size - 1} tags from /tag-list")
                tags.toTypedArray()
            }
        } catch (e: Exception) {
            // Return popular tags as fallback
            android.util.Log.e("PapalahFilters", "Error fetching tags: ${e.message}")
            getPopularTags()
        }
    }
}

typealias Tags = Array<Tag>
typealias Tag = Pair<String, String>
