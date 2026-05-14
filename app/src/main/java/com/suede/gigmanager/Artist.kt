package com.suede.gigmanager

data class Artist(
    val id: Int,
    val name: String,
    val imageUrl: String?
)

data class ArtistListResponse(
    val artists: List<Artist>,
    val hasOrphanedTours: Boolean
)

data class DeezerArtist(
    val id: Long,
    val name: String,
    val imageUrl: String?
)
