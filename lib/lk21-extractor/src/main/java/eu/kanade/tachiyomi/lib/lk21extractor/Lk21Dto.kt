package eu.kanade.tachiyomi.lib.lk21extractor
import kotlinx.serialization.Serializable

@Serializable
data class Lk21Response(
    val url: String? = null,
    val status: String? = null
)
