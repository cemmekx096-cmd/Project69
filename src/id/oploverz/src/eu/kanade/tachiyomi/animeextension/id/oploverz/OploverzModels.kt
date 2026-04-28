package eu.kanade.tachiyomi.animeextension.id.oploverz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ========================= Meta =========================

@Serializable
data class Meta(
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)

// ========================= Series =========================

@Serializable
data class SeriesListResponse(
    val meta: Meta,
    val data: List<SeriesItem>,
)

@Serializable
data class SeriesDetailResponse(
    val data: SeriesDetail,
)

@Serializable
data class SeriesItem(
    val id: Int,
    val title: String,
    val slug: String,
    val poster: String? = null,
    val status: String? = null,
    val score: Double? = null,
    val releaseType: String? = null,
    val genres: List<Genre>? = null,
)

@Serializable
data class SeriesDetail(
    val id: Int,
    val seriesId: Int,
    val title: String,
    val japaneseTitle: String? = null,
    val slug: String,
    val description: String? = null,
    val poster: String? = null,
    val status: String? = null,
    val score: Double? = null,
    val releaseType: String? = null,
    val duration: String? = null,
    val genres: List<Genre>? = null,
    val season: Season? = null,
    val studio: Studio? = null,
    val releaseDate: String? = null,
)

@Serializable
data class Genre(
    val id: Int,
    val name: String,
    val slug: String,
)

@Serializable
data class Season(
    val id: Int,
    val name: String,
    val slug: String,
)

@Serializable
data class Studio(
    val id: Int,
    val name: String,
    val slug: String,
)

// ========================= Episodes =========================

@Serializable
data class EpisodeListResponse(
    val meta: Meta,
    val data: List<EpisodeItem>,
)

@Serializable
data class EpisodeDetailResponse(
    val data: EpisodeDetail,
)

@Serializable
data class EpisodeItem(
    val id: Int,
    val episodeNumber: String,
    val title: String? = null,
    val releasedAt: String? = null,
    val series: SeriesItem? = null,
)

@Serializable
data class EpisodeDetail(
    val id: Int,
    val episodeNumber: String,
    val title: String? = null,
    val streamUrl: List<StreamUrl>? = null,
    val downloadUrl: List<DownloadFormat>? = null,
    val releasedAt: String? = null,
    val series: SeriesDetail? = null,
)

@Serializable
data class StreamUrl(
    val source: String,
    val url: String,
)

@Serializable
data class DownloadFormat(
    val format: String,
    val resolutions: List<Resolution>,
)

@Serializable
data class Resolution(
    val quality: String,
    @SerialName("download_links")
    val downloadLinks: List<DownloadLink>,
)

@Serializable
data class DownloadLink(
    val host: String,
    val url: String,
)

// ========================= Genres =========================

@Serializable
data class GenreListResponse(
    val meta: Meta,
    val data: List<Genre>,
)

// ========================= Seasons =========================

@Serializable
data class SeasonListResponse(
    val data: List<Season>,
)
