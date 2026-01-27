package eu.kanade.tachiyomi.animeextension.id.anichin

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnichinFilters {
    fun getSearchParameters(filters: AnimeFilterList): Params {
        val params = Params()
        filters.forEach { f ->
            when (f) {
                is GenreFilter -> {
                    val part = f.toUriPart()
                    if (part.isNotEmpty()) params.genre = part
                }
            }
        }
        return params
    }

    class Params {
        var genre: String = ""
    }

    class GenreFilter : UriPartFilter(
        "Genres",
        arrayOf(
            Pair("<select>", ""),
            Pair("Action", "action"),
            Pair("Action Drama", "action-drama"),
            Pair("Actions", "actions"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Historical", "historical"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Romance", "romance"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Supernatural", "supernatural"),
            Pair("Thriller", "thriller"),
            Pair("Wuxia", "wuxia"),
            Pair("Xianxia", "xianxia"),
            Pair("Xuanhuan", "xuanhuan"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
