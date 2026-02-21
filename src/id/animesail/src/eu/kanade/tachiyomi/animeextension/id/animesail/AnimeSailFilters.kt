package eu.kanade.tachiyomi.animeextension.id.animesail

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/**
 * Filters for AnimeSail extension
 * 
 * Provides filtering options for:
 * - Genre (Action, Comedy, etc)
 * - Year (2024, 2023, etc)
 * - Type (Anime, Donghua, OVA)
 */
object AnimeSailFilters {
    
    // ==================== Filter Classes ====================
    
    class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("<Pilih Genre>", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Horror", "horror"),
            Pair("Mecha", "mecha"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Thriller", "thriller"),
        )
    )
    
    class YearFilter : UriPartFilter(
        "Tahun",
        arrayOf(
            Pair("<Pilih Tahun>", ""),
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
        )
    )
    
    class TypeFilter : UriPartFilter(
        "Tipe",
        arrayOf(
            Pair("<Pilih Tipe>", ""),
            Pair("Anime", "anime"),
            Pair("Donghua", "donghua"),
            Pair("OVA", "ova"),
            Pair("Movie", "movie"),
            Pair("Special", "special"),
        )
    )
    
    // ==================== Base Filter Class ====================
    
    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>
    ) : AnimeFilter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
        fun isEmpty() = vals[state].second.isEmpty()
    }
    
    // ==================== Filter List Builder ====================
    
    fun getFilterList(): AnimeFilterList {
        return AnimeFilterList(
            AnimeFilter.Header("NOTE: Filter diabaikan jika search query tidak kosong!"),
            AnimeFilter.Separator(),
            GenreFilter(),
            YearFilter(),
            TypeFilter(),
        )
    }
}
