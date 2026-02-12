package eu.kanade.tachiyomi.animeextension.id.lk21movies

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

internal object LK21MoviesFilters {

    // ========================== Sort Filter ===============================
    class SortFilter : UriPartFilter(
        "Urutan",
        arrayOf(
            Tag("Latest (Terbaru)", "latest"),
            Tag("Popular (Populer)", "populer"),
        ),
    )

    // ========================== Type Filter ===============================
    class TypeFilter : UriPartFilter(
        "Tipe",
        arrayOf(
            Tag("- Pilih Tipe -", ""),
            Tag("Movie", "movie"),
        ),
    )

    // ======================== Genre 1 Filter ==============================
    class GenreFilter : UriPartFilter(
        "Genre 1",
        arrayOf(
            Tag("- Pilih Genre 1 -", ""),
            Tag("Action", "action"),
            Tag("Adventure", "adventure"),
            Tag("Animation", "animation"),
            Tag("Comedy", "comedy"),
            Tag("Crime", "crime"),
            Tag("Documentary", "documentary"),
            Tag("Drama", "drama"),
            Tag("Family", "family"),
            Tag("Fantasy", "fantasy"),
            Tag("History", "history"),
            Tag("Horror", "horror"),
            Tag("Music", "music"),
            Tag("Mystery", "mystery"),
            Tag("Romance", "romance"),
            Tag("Science Fiction", "science-fiction"),
            Tag("Thriller", "thriller"),
            Tag("War", "war"),
            Tag("Western", "western"),
        ),
    )

    // ======================== Genre 2 Filter ==============================
    class Genre2Filter : UriPartFilter(
        "Genre 2",
        arrayOf(
            Tag("- Pilih Genre 2 -", ""),
            Tag("Action", "action"),
            Tag("Adventure", "adventure"),
            Tag("Animation", "animation"),
            Tag("Comedy", "comedy"),
            Tag("Crime", "crime"),
            Tag("Documentary", "documentary"),
            Tag("Drama", "drama"),
            Tag("Family", "family"),
            Tag("Fantasy", "fantasy"),
            Tag("History", "history"),
            Tag("Horror", "horror"),
            Tag("Music", "music"),
            Tag("Mystery", "mystery"),
            Tag("Romance", "romance"),
            Tag("Science Fiction", "science-fiction"),
            Tag("Thriller", "thriller"),
            Tag("War", "war"),
            Tag("Western", "western"),
        ),
    )

    // ======================== Country Filter ==============================
    class CountryFilter : UriPartFilter(
        "Negara",
        arrayOf(
            Tag("- Pilih Negara -", ""),
            Tag("USA", "usa"),
            Tag("United Kingdom", "united-kingdom"),
            Tag("Indonesia", "indonesia"),
            Tag("Korea", "korea"),
            Tag("Japan", "japan"),
            Tag("China", "china"),
            Tag("Hong Kong", "hong-kong"),
            Tag("Thailand", "thailand"),
            Tag("India", "india"),
            Tag("France", "france"),
            Tag("Germany", "germany"),
            Tag("Spain", "spain"),
            Tag("Italy", "italy"),
            Tag("Canada", "canada"),
            Tag("Australia", "australia"),
            Tag("Brazil", "brazil"),
            Tag("Mexico", "mexico"),
            Tag("Russia", "russia"),
            Tag("Turkey", "turkey"),
            Tag("Philippines", "philippines"),
            Tag("Malaysia", "malaysia"),
            Tag("Singapore", "singapore"),
            Tag("Taiwan", "taiwan"),
            Tag("Vietnam", "vietnam"),
        ),
    )

    // ======================== Year Filter =================================
    class YearFilter : UriPartFilter(
        "Tahun",
        generateYearList(),
    )

    // ======================== Base Filter Class ===========================
    open class UriPartFilter(displayName: String, private val options: Tags) :
        AnimeFilter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
        fun toUriPart() = options[state].second
        fun isEmpty() = options[state].second == ""
        fun isDefault() = state == 0
    }

    // ====================== Generate Year List ============================
    private fun generateYearList(): Tags {
        val currentYear = 2026 // Sesuaikan dengan tahun saat ini
        val years = mutableListOf(Tag("- Pilih Tahun -", ""))

        // Generate dari tahun sekarang mundur ke 1970
        for (year in currentYear downTo 1970) {
            years.add(Tag(year.toString(), year.toString()))
        }

        return years.toTypedArray()
    }
}

typealias Tags = Array<Tag>
typealias Tag = Pair<String, String>
