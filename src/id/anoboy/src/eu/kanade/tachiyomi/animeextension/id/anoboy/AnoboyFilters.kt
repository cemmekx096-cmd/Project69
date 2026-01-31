package eu.kanade.tachiyomi.animeextension.id.anoboy

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnoboyFilters {

    fun getSearchParameters(filters: AnimeFilterList): Params {
        val params = Params()
        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> params.status = filter.toUriPart()
                is TypeFilter -> params.type = filter.toUriPart()
                is OrderFilter -> params.order = filter.toUriPart()
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { params.genres.add(it.value) }
                }
                else -> Unit
            }
        }
        return params
    }

    class Params {
        var status: String = ""
        var type: String = ""
        var order: String = ""
        val genres: MutableList<String> = mutableListOf()
    }

    // --- Filter Class Definitions ---

    open class UriPartFilter(name: String, val vals: Array<Pair<String, String>>) :
        AnimeFilter.Select<String>(name, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        ),
    )

    class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("TV Series", "tv"),
            Pair("Movie", "movie"),
            Pair("OVA", "ova"),
            Pair("Special", "special"),
        ),
    )

    class OrderFilter : UriPartFilter(
        "Order by",
        arrayOf(
            Pair("Latest", "latest"),
            Pair("Popular", "popular"),
            Pair("Rating", "rating"),
            Pair("A-Z", "alphabet"),
        ),
    )

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

    class GenreCheckBox(name: String, val value: String) : AnimeFilter.CheckBox(name)
}
