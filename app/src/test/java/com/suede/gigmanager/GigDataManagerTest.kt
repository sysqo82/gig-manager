package com.suede.gigmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GigDataManagerTest {

    private lateinit var context: Context
    private lateinit var dataManager: GigDataManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear files and prefs
        context.filesDir.listFiles()?.forEach { it.delete() }
        context.getSharedPreferences("gig_manager_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        dataManager = GigDataManager(context)
    }

    @Test
    fun testDefaultTourCreatedWhenNoData() {
        val tours = dataManager.loadTours()
        assertEquals(1, tours.size)
        assertEquals("New Tour", tours[0].name)
        assertTrue(tours[0].gigs.isEmpty())
    }

    @Test
    fun testAddTour() {
        val tour = dataManager.addTour("European Tour 2026")
        assertNotNull(tour)
        assertEquals("European Tour 2026", tour?.name)

        val tours = dataManager.loadTours()
        assertEquals(2, tours.size)
        assertEquals("European Tour 2026", tours[0].name)
    }

    @Test
    fun testRenameTour() {
        val toursBefore = dataManager.loadTours()
        val tourId = toursBefore[0].id

        val renamed = dataManager.renameTour(tourId, "World Tour")
        assertTrue(renamed)

        val tour = dataManager.getTour(tourId)
        assertNotNull(tour)
        assertEquals("World Tour", tour?.name)
    }

    @Test
    fun testDeleteTour() {
        val tour = dataManager.addTour("Tour To Delete")
        assertNotNull(tour)

        val deleted = dataManager.deleteTour(tour!!.id)
        assertTrue(deleted)

        val remaining = dataManager.loadTours()
        assertNull(remaining.find { it.id == tour.id })
    }

    @Test
    fun testAddGigToTourAndSorting() {
        val tours = dataManager.loadTours()
        val tourId = tours[0].id

        val gig1 = Gig(
            date = "Sunday, 20/6/2026",
            cityVenue = "Manchester Albert Hall",
            ticketsWithMe = "Yes",
            whereTicketsAre = "App",
            accommodation = "Hotel",
            whereAccomBought = "Direct",
            accomDates = "20/6/2026 - 21/6/2026",
            cost = "80",
            paid = "Yes",
            accomComments = null,
            travelDetails = "Train"
        )

        val gig2 = Gig(
            date = "Friday, 12/6/2026",
            cityVenue = "London O2 Forum",
            ticketsWithMe = "Yes",
            whereTicketsAre = "Email",
            accommodation = "Premier Inn",
            whereAccomBought = "Direct",
            accomDates = "12/6/2026 - 13/6/2026",
            cost = "90",
            paid = "Yes",
            accomComments = null,
            travelDetails = "Car"
        )

        dataManager.addGigToTour(tourId, gig1)
        dataManager.addGigToTour(tourId, gig2)

        val updatedTour = dataManager.getTour(tourId)
        assertNotNull(updatedTour)
        assertEquals(2, updatedTour?.gigs?.size)
        // Earlier date (12/6) should be sorted first before later date (20/6)
        assertTrue(updatedTour?.gigs?.get(0)?.cityVenue?.contains("London") == true)
        assertTrue(updatedTour?.gigs?.get(1)?.cityVenue?.contains("Manchester") == true)
    }

    @Test
    fun testUpdateGigInTour() {
        val tours = dataManager.loadTours()
        val tourId = tours[0].id

        val gig = Gig(
            date = "Saturday, 1/8/2026",
            cityVenue = "Brighton Dome",
            ticketsWithMe = "Yes",
            whereTicketsAre = "App",
            accommodation = null,
            whereAccomBought = null,
            accomDates = null,
            cost = "50",
            paid = "Yes",
            accomComments = null,
            travelDetails = "Bus"
        )

        dataManager.addGigToTour(tourId, gig)

        val updatedGig = gig.copy(cityVenue = "Brighton Centre")
        val success = dataManager.updateGigInTour(tourId, 0, updatedGig)
        assertTrue(success)

        val tour = dataManager.getTour(tourId)
        assertEquals("Brighton Centre", tour?.gigs?.get(0)?.cityVenue)
    }

    @Test
    fun testDeleteGigFromTour() {
        val tours = dataManager.loadTours()
        val tourId = tours[0].id

        val gig = Gig(
            date = "Sunday, 2/8/2026",
            cityVenue = "Bristol Anson Rooms",
            ticketsWithMe = "Yes",
            whereTicketsAre = null,
            accommodation = null,
            whereAccomBought = null,
            accomDates = null,
            cost = "40",
            paid = "Yes",
            accomComments = null,
            travelDetails = null
        )

        dataManager.addGigToTour(tourId, gig)
        assertEquals(1, dataManager.getTour(tourId)?.gigs?.size)

        val deleted = dataManager.deleteGigFromTour(tourId, 0)
        assertTrue(deleted)
        assertEquals(0, dataManager.getTour(tourId)?.gigs?.size)
    }

    @Test
    fun testArchiveTour() {
        val gig = Gig(
            date = "Monday, 10/8/2026",
            cityVenue = "Glasgow Barrowland",
            ticketsWithMe = "Yes",
            whereTicketsAre = null,
            accommodation = null,
            whereAccomBought = null,
            accomDates = null,
            cost = "60",
            paid = "Yes",
            accomComments = null,
            travelDetails = null
        )
        dataManager.addGig(gig)

        val archived = dataManager.archiveTour("Spring Tour 2026")
        assertTrue(archived)

        val tours = dataManager.loadTours()
        // Should have new current empty tour + archived tour
        assertEquals(2, tours.size)
        assertEquals("Spring Tour 2026", tours[1].name)
        assertTrue(tours[1].gigs.all { it.isArchived == true })
    }

    @Test
    fun testRestoreFromServer() {
        val gig = Gig(
            date = "Wednesday, 15/9/2026",
            cityVenue = "Newcastle O2 Academy",
            ticketsWithMe = "Yes",
            whereTicketsAre = null,
            accommodation = null,
            whereAccomBought = null,
            accomDates = null,
            cost = "55",
            paid = "Yes",
            accomComments = null,
            travelDetails = null
        )
        val archives = listOf(
            TourArchive("Old Tour 2025", "10 January 2025", listOf(gig.copy(isArchived = true)))
        )

        dataManager.restoreFromServer("Autumn Tour 2026", listOf(gig), archives)

        val tours = dataManager.loadTours()
        assertEquals(2, tours.size)
        assertEquals("Autumn Tour 2026", tours[0].name)
        assertEquals(1, tours[0].gigs.size)
        assertEquals("Old Tour 2025", tours[1].name)
    }
}
