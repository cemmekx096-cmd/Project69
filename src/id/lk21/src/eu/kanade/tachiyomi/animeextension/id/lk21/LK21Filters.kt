package eu.kanade.tachiyomi.animeextension.id.lk21

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object LK21Filters {

    fun getSearchParameters(filters: AnimeFilterList): Params {
        val params = Params()
        
        ReportLog.reportDebug("LK21-Filters", "Processing ${filters.size} filters")
        
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val part = filter.toUriPart()
                    if (part.isNotEmpty()) {
                        params.genre = part
                        ReportLog.reportDebug("LK21-Filters", "Genre selected: $part")
                    }
                }
                is CountryFilter -> {
                    val part = filter.toUriPart()
                    if (part.isNotEmpty()) {
                        params.country = part
                        ReportLog.reportDebug("LK21-Filters", "Country selected: $part")
                    }
                }
                else -> Unit
            }
        }
        
        return params
    }

    fun getFilterList(): AnimeFilterList {
        ReportLog.reportDebug("LK21-Filters", "Creating filter list")
        return AnimeFilterList(
            AnimeFilter.Header("NOTE: Filters are ignored if using text search!"),
            AnimeFilter.Separator(),
            GenreFilter(),
            CountryFilter(),
        )
    }

    class Params {
        var genre: String = ""
        var country: String = ""
    }

    class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("<select>", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Animation", "animation"),
            Pair("Biography", "biography"),
            Pair("Comedy", "comedy"),
            Pair("Crime", "crime"),
            Pair("Documentary", "documentary"),
            Pair("Drama", "drama"),
            Pair("Family", "family"),
            Pair("Fantasy", "fantasy"),
            Pair("History", "history"),
            Pair("Horror", "horror"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("Romance", "romance"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Sport", "sport"),
            Pair("Thriller", "thriller"),
            Pair("War", "war"),
            Pair("Western", "western"),
        ),
    )

    class CountryFilter : UriPartFilter(
        "Country",
        arrayOf(
            Pair("<select>", ""),
            Pair("USA", "usa"),
            Pair("Australia", "australia"),
            Pair("China", "china"),
            Pair("France", "france"),
            Pair("Japan", "japan"),
            Pair("South Korea", "south-korea"),
            Pair("Malaysia", "malaysia"),
            Pair("Russia", "russia"),
            Pair("Thailand", "thailand"),
            Pair("India", "india"),
            Pair("Indonesia", "indonesia"),
            Pair("United Kingdom", "united-kingdom"),
            Pair("Germany", "germany"),
            Pair("Italy", "italy"),
            Pair("Spain", "spain"),
            Pair("Brazil", "brazil"),
            Pair("Mexico", "mexico"),
            Pair("Turkey", "turkey"),
            Pair("Philippines", "philippines"),
            Pair("Vietnam", "vietnam"),
        ),
    )

    open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
