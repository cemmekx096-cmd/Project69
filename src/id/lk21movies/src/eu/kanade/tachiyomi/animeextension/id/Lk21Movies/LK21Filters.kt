package eu.kanade.tachiyomi.animeextension.id.lk21movies

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object LK21Filters {

    val FILTER_LIST get() = AnimeFilterList(
        AnimeFilter.Header("NOTE: Filter tidak bisa digabung!"),
        AnimeFilter.Separator(),
        GenreFilter(),
        YearFilter(),
        CountryFilter(),
    )

    private class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("<Pilih>", ""),
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
            Pair("Film-Noir", "film-noir"),
            Pair("History", "history"),
            Pair("Horror", "horror"),
            Pair("Music", "music"),
            Pair("Musical", "musical"),
            Pair("Mystery", "mystery"),
            Pair("Romance", "romance"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Sport", "sport"),
            Pair("Thriller", "thriller"),
            Pair("War", "war"),
            Pair("Western", "western"),
        ),
    )

    private class YearFilter : UriPartFilter(
        "Tahun",
        arrayOf(
            Pair("<Pilih>", ""),
            Pair("2026", "2026"),
            Pair("2025", "2025"),
            Pair("2024", "2024"),
            Pair("2023", "2023"),
            Pair("2022", "2022"),
            Pair("2021", "2021"),
            Pair("2020", "2020"),
            Pair("2019", "2019"),
            Pair("2018", "2018"),
            Pair("2017", "2017"),
            Pair("2016", "2016"),
            Pair("2015", "2015"),
            Pair("2014", "2014"),
            Pair("2013", "2013"),
            Pair("2012", "2012"),
            Pair("2011", "2011"),
            Pair("2010", "2010"),
        ),
    )

    private class CountryFilter : UriPartFilter(
        "Negara",
        arrayOf(
            Pair("<Pilih>", ""),
            Pair("Indonesia", "indonesia"),
            Pair("USA", "usa"),
            Pair("United Kingdom", "united-kingdom"),
            Pair("Korea", "korea"),
            Pair("Japan", "japan"),
            Pair("China", "china"),
            Pair("Thailand", "thailand"),
            Pair("Hong Kong", "hong-kong"),
            Pair("India", "india"),
            Pair("France", "france"),
            Pair("Germany", "germany"),
            Pair("Spain", "spain"),
            Pair("Italy", "italy"),
            Pair("Canada", "canada"),
            Pair("Australia", "australia"),
        ),
    )

    private open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
