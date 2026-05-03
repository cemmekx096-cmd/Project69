package eu.kanade.tachiyomi.lib.lk21database

data class Lk21Film(
    val id: String,
    val title: String,
    val slug: String,
    val poster: String,   // raw path, e.g. "2026/05/film-xxx.jpg"
    val type: String,     // "movie" atau "series"
    val year: Int,
    val quality: String,
    val rating: Double,
    val runtime: String,
    val episode: String,
    val season: String,
    val isComplete: Int,
)

data class Lk21DbIndex(
    val version: String,  // format "YYYY-MM-DD"
    val total: Int,
    val url: String,      // raw GitHub URL ke lk21_data.json
)
