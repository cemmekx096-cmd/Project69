package eu.kanade.tachiyomi.animeextension.id.lk21movies

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

// 1 & 2. Filter Genre (Digunakan untuk Genre 1 dan Genre 2)
class GenreFilter(name: String, genres: Array<String>) : AnimeFilter.Select<String>(name, genres)

// 3. Filter Tahun
class YearFilter(years: Array<String>) : AnimeFilter.Select<String>("Tahun", years)

// 4. Filter Negara
class CountryFilter(countries: Array<String>) : AnimeFilter.Select<String>("Negara", countries)

// 5. Filter Berdasarkan (Statis - Logic Kita Sendiri)
class SortFilter : AnimeFilter.Select<String>(
    "Berdasarkan",
    arrayOf(
        "Default",
        "Rating Tertinggi",
        "Paling Banyak Dilihat",
        "Rating 9+",
        "Rating 8+",
        "Rating 7+",
        "Web-DL",
        "Bluray"
    )
)

// Objek Helper untuk menampung hasil Live Scraping
object Lk21Filters {
    var genres = arrayOf("Loading...")
    var countries = arrayOf("Loading...")
    var years = arrayOf("Loading...")

    val staticFilter = SortFilter()
}
