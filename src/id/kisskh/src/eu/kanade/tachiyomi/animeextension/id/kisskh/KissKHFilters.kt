package eu.kanade.tachiyomi.animeextension.id.kisskh

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object KissKHFilters {

    data class FilterParams(
        val type: String = "0",
        val country: String = "0",
        val status: String = "0",
        val sub: String = "0",
        val order: String = "1",
    )

    fun getSearchParameters(filters: AnimeFilterList): FilterParams {
        var type = "0"; var country = "0"; var status = "0"
        var sub = "0"; var order = "1"
        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> type = filter.selected()
                is CountryFilter -> country = filter.selected()
                is StatusFilter -> status = filter.selected()
                is SubFilter -> sub = filter.selected()
                is OrderFilter -> order = filter.selected()
                else -> {}
            }
        }
        return FilterParams(type, country, status, sub, order)
    }

    fun getFilterList() = AnimeFilterList(
        OrderFilter(),
        TypeFilter(),
        CountryFilter(),
        StatusFilter(),
        SubFilter(),
    )

    // ── Filter Classes ────────────────────────────────────────

    private class OrderFilter : SelectFilter(
        "Order",
        listOf(
            Pair("Popular", "1"),
            Pair("Latest", "2"),
        ),
    )

    private class TypeFilter : SelectFilter(
        "Type",
        listOf(
            Pair("All", "0"),
            Pair("TV Series", "1"),
            Pair("Movie", "2"),
            Pair("Anime", "3"),
            Pair("Hollywood", "4"),
        ),
    )

    private class CountryFilter : SelectFilter(
        "Country",
        listOf(
            Pair("All", "0"),
            Pair("Chinese", "1"),
            Pair("Korea", "2"),
            Pair("Japanese", "3"),
            Pair("Hongkong", "4"),
            Pair("Thailand", "5"),
            Pair("United States", "6"),
            Pair("Taiwan", "7"),
            Pair("Philippines", "8"),
        ),
    )

    private class StatusFilter : SelectFilter(
        "Status",
        listOf(
            Pair("All", "0"),
            Pair("Ongoing", "1"),
            Pair("Completed", "2"),
            Pair("Upcoming", "3"),
        ),
    )

    private class SubFilter : SelectFilter(
        "Subtitle",
        listOf(
            Pair("All", "0"),
            Pair("English", "1"),
            Pair("Khmer", "2"),
            Pair("Indonesia", "3"),
            Pair("Malay", "4"),
            Pair("Thai", "5"),
            Pair("Arabic", "10"),
        ),
    )

    // ── Base SelectFilter ─────────────────────────────────────

    open class SelectFilter(
        name: String,
        private val options: List<Pair<String, String>>,
        defaultIndex: Int = 0,
    ) : AnimeFilter.Select<String>(name, options.map { it.first }.toTypedArray(), defaultIndex) {
        fun selected() = options[state].second
    }
}
