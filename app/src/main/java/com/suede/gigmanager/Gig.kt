package com.suede.gigmanager

import com.google.gson.annotations.SerializedName

data class Gig(
    val date: String?,
    val cityVenue: String?,
    val ticketsWithMe: String?,
    val whereTicketsAre: String?,
    @SerializedName("location")
    val accommodation: String?,
    val whereAccomBought: String?,
    val accomDates: String?,
    val cost: String?,
    val paid: String?,
    val accomComments: String?,
    val travelDetails: String?,
    val isComplete: Boolean? = false
)
