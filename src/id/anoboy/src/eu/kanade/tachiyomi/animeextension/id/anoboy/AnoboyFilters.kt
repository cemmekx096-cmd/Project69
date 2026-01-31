package eu.kanade.tachiyomi.animeextension.id.anoboy

import eu.kanade.tachiyomi.animesource.model.AnimeFilter

object AnoboyFilters {

    // Data class untuk search parameters
    data class SearchParameters(
        val status: String = "",
        val type: String = "",
        val order: String = "update",
        val genres: List<String> = emptyList()
    )

    // Extract parameters dari filter list
    fun getSearchParameters(filters: AnimeFilterList): SearchParameters {
        var status = ""
        var type = ""
        var order = "update"
        val genres = mutableListOf<String>()

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> {
                    status = filter.toUriPart()
                }
                is TypeFilter -> {
                    type = filter.toUriPart()
                }
                is OrderFilter -> {
                    order = filter.toUriPart()
                }
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { genres.add(it.value) }
                }
                else -> {}
            }
        }

        return SearchParameters(status, type, order, genres)
    }

    // ============================== Filters ===============================

    class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed")
        )
    )

    class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", ""),
            Pair("TV", "TV"),
            Pair("Movie", "Movie"),
            Pair("OVA", "OVA"),
            Pair("ONA", "ONA"),
            Pair("Special", "Special")
        )
    )

    class OrderFilter : UriPartFilter(
        "Order By",
        arrayOf(
            Pair("Latest Update", "update"),
            Pair("Popular", "popular"),
            Pair("Title", "title")
        )
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
            GenreCheckBox("Yuri", "yuri")
        )
    )

    // ============================== Helpers ===============================

    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray()
    ) {
        fun toUriPart() = vals[state].second
    }

    class GenreCheckBox(name: String, val value: String) : AnimeFilter.CheckBox(name)
}
