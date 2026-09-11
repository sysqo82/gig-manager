package com.suede.gigmanager

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GigDataTest {

    private val gson = Gson()

    @Test
    fun testGigDataDefaults() {
        val gig = Gig(
            date = "Friday, 12/5/2026",
            cityVenue = "London O2",
            ticketsWithMe = "Yes",
            whereTicketsAre = "Email",
            accommodation = "Hotel",
            whereAccomBought = "Booking.com",
            accomDates = "12/5/2026 - 13/5/2026",
            cost = "100",
            paid = "Yes",
            accomComments = "Nice hotel",
            travelDetails = "Train"
        )

        assertEquals("Friday, 12/5/2026", gig.date)
        assertEquals("London O2", gig.cityVenue)
        assertFalse(gig.isArchived ?: true)
        assertFalse(gig.isComplete ?: true)
        assertTrue(gig.hasReturnJourney ?: false)
    }

    @Test
    fun testGigSerializationAndDeserialization() {
        val gig = Gig(
            date = "Saturday, 13/5/2026",
            cityVenue = "Manchester Arena",
            ticketsWithMe = "Yes",
            whereTicketsAre = null,
            accommodation = "Hotel Indigo",
            whereAccomBought = "Direct",
            accomDates = null,
            cost = "150",
            paid = "Yes",
            accomComments = null,
            travelDetails = "Car",
            travelDate = "13/5/2026",
            travelFromPlace = "London",
            travelFromTime = "10:00",
            travelToPlace = "Manchester",
            travelToTime = "14:00",
            outboundInfo = "Seat 12A",
            hasReturnJourney = true,
            returnDate = "14/5/2026",
            returnFromPlace = "Manchester",
            returnFromTime = "11:00",
            returnToPlace = "London",
            returnToTime = "15:00",
            generalComments = "Bring merch",
            isComplete = true,
            isArchived = false
        )

        val json = gson.toJson(gig)
        val deserialized = gson.fromJson(json, Gig::class.java)

        assertEquals(gig.cityVenue, deserialized.cityVenue)
        assertEquals(gig.accommodation, deserialized.accommodation)
        assertEquals(gig.travelFromPlace, deserialized.travelFromPlace)
        assertEquals(gig.outboundInfo, deserialized.outboundInfo)
        assertEquals(gig.isComplete, deserialized.isComplete)
    }

    @Test
    fun testTourCreation() {
        val tour = Tour(
            name = "UK Summer Tour",
            createdDate = "1 May 2026"
        )

        assertNotNull(tour.id)
        assertEquals("UK Summer Tour", tour.name)
        assertTrue(tour.gigs.isEmpty())
        assertEquals("1 May 2026", tour.createdDate)
    }

    @Test
    fun testArtistModel() {
        val artist = Artist(id = 1, name = "Suede", imageUrl = "https://example.com/suede.jpg")
        assertEquals(1, artist.id)
        assertEquals("Suede", artist.name)
        assertEquals("https://example.com/suede.jpg", artist.imageUrl)
    }
}
