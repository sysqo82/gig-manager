package com.suede.gigmanager

import java.util.UUID

data class Tour(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val gigs: List<Gig> = emptyList(),
    val createdDate: String? = null
)
