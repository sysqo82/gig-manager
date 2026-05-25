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
    val travelDate: String? = null,
    val travelFromPlace: String? = null,
    val travelFromTime: String? = null,
    val travelToPlace: String? = null,
    val travelToTime: String? = null,
    val outboundInfo: String? = null,
    val hasReturnJourney: Boolean? = true,
    val returnDate: String? = null,
    val returnFromPlace: String? = null,
    val returnFromTime: String? = null,
    val returnToPlace: String? = null,
    val returnToTime: String? = null,
    val generalComments: String? = null,
    val isComplete: Boolean? = false,
    val isArchived: Boolean? = false
)
