package eu.kanade.tachiyomi.animeextension.id.anoboy

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnoboyFilters {

    // Fungsi untuk mendapatkan parameter pencarian dari filter list
    fun getSearchParameters(filters: AnimeFilterList): Params {
        val params = Params()
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }// Filter yang aktif
                        .forEach { params.genres.add(it.value) }// Tambahkan genre yang dipilih
                }
                else -> Unit
            }
        }
        return params
    }

    // Kelas Params untuk menyimpan genre yang dipilih
    class Params {
        val genres: MutableList<String> = mutableListOf()// List genre yang dipilih
    }

    // Genre filter untuk memilih genre tertentu
    class GenreFilter : AnimeFilter.Group<GenreCheckBox>(
        "Genres",
        listOf(
            GenreCheckBox("Action", "action"),
            GenreCheckBox("Adult Cast", "adult-cast"),
            GenreCheckBox("Adventure", "adventure"),
            GenreCheckBox("Anthropomorphic", "anthropomorphic"),
            GenreCheckBox("Cars", "cars"),
            GenreCheckBox("Comedy", "comedy"),
            GenreCheckBox("Dementia", "dementia"),
            GenreCheckBox("Demons", "demons"),
            GenreCheckBox("Drama", "drama"),
            GenreCheckBox("Dub", "dub"),
            GenreCheckBox("Ecchi", "ecchi"),
            GenreCheckBox("Fantasy", "fantasy"),
            GenreCheckBox("Game", "game"),
            GenreCheckBox("Harem", "harem"),
            GenreCheckBox("Historical", "historical"),
            GenreCheckBox("Horror", "horror"),
            GenreCheckBox("Josei", "josei"),
            GenreCheckBox("Kids", "kids"),
            GenreCheckBox("Magic", "magic"),
            GenreCheckBox("Martial Arts", "martial-arts"),
            GenreCheckBox("Mecha", "mecha"),
            GenreCheckBox("Military", "military"),
            GenreCheckBox("Music", "music"),
            GenreCheckBox("Mystery", "mystery"),
            GenreCheckBox("Parody", "parody"),
            GenreCheckBox("Police", "police"),
            GenreCheckBox("Psychological", "psychological"),
            GenreCheckBox("Romance", "romance"),
            GenreCheckBox("Samurai", "samurai"),
            GenreCheckBox("School", "school"),
            GenreCheckBox("Sci-Fi", "sci-fi"),
            GenreCheckBox("Seinen", "seinen"),
            GenreCheckBox("Shoujo", "shoujo"),
            GenreCheckBox("Shoujo Ai", "shoujo-ai"),
            GenreCheckBox("Shounen", "shounen"),
            GenreCheckBox("Shounen Ai", "shounen-ai"),
            GenreCheckBox("Slice of Life", "slice-of-life"),
            GenreCheckBox("Space", "space"),
            GenreCheckBox("Sports", "sports"),
            GenreCheckBox("Super Power", "super-power"),
            GenreCheckBox("Supernatural", "supernatural"),
            GenreCheckBox("Thriller", "thriller"),
            GenreCheckBox("Vampire", "vampire"),
            GenreCheckBox("Yaoi", "yaoi"),
            GenreCheckBox("Yuri", "yuri"),
        ),
    )
    // Kelas GenreCheckBox untuk genre yang dapat dipilih
    class GenreCheckBox(name: String, val value: String) : AnimeFilter.CheckBox(name)
}
