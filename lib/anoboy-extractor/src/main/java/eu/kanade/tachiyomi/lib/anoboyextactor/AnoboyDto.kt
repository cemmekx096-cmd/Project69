package eu.kanade.tachiyomi.lib.anoboyextractor
import kotlinx.serialization.Serializable

@Serializable
data class AnoboyVideo(
    val file: String? = null,
    val label: String? = null,
    val type: String? = null
)
