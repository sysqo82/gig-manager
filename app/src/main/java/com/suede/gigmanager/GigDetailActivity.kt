package com.suede.gigmanager

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class GigDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GIG_INDEX = "extra_gig_index"
        const val EXTRA_ARTIST_ID = "extra_artist_id"
        const val EXTRA_TOUR_ID = "extra_tour_id"

        fun start(context: Context, tourId: String, gigIndex: Int, artistId: Int) {
            context.startActivity(Intent(context, GigDetailActivity::class.java).apply {
                putExtra(EXTRA_TOUR_ID, tourId)
                putExtra(EXTRA_GIG_INDEX, gigIndex)
                putExtra(EXTRA_ARTIST_ID, artistId)
            })
        }
    }

    private var gigIndex = -1
    private var artistId: Int = 0
    private lateinit var tourId: String
    private lateinit var gig: Gig
    private val dataManager by lazy { GigDataManager(this, artistId) }

    // View mode fields
    private lateinit var completeBadge: TextView
    private lateinit var gigDateText: TextView
    private lateinit var cityVenueText: TextView
    private lateinit var ticketsWithMeText: TextView
    private lateinit var whereTicketsText: TextView
    private lateinit var accommodationText: TextView
    private lateinit var whereAccomBoughtText: TextView
    private lateinit var accomDatesText: TextView
    private lateinit var costText: TextView
    private lateinit var paidText: TextView
    private lateinit var accomCommentsText: TextView
    private lateinit var travelDetailsText: TextView
    private lateinit var travelDateText: TextView
    private lateinit var generalCommentsText: TextView
    private lateinit var travelFromText: TextView
    private lateinit var travelToText: TextView
    private lateinit var outboundInfoText: TextView
    private lateinit var returnJourneySection: LinearLayout
    private lateinit var returnDateText: TextView
    private lateinit var returnFromText: TextView
    private lateinit var returnToText: TextView

    // Edit mode fields
    private lateinit var editDate: EditText
    private lateinit var editCityVenue: EditText
    private lateinit var spinnerTicketsWithMe: Spinner
    private lateinit var editWhereTickets: EditText
    private lateinit var editAccommodation: EditText
    private lateinit var editWhereAccomBought: EditText
    private lateinit var editAccomDates: EditText
    private lateinit var editCost: EditText
    private lateinit var spinnerPaid: Spinner
    private lateinit var editAccomComments: EditText
    private lateinit var editTravelDetails: EditText
    private lateinit var editTravelDate: EditText
    private lateinit var editGeneralComments: EditText
    private lateinit var editOutboundSection: LinearLayout
    private lateinit var editTravelFromPlace: EditText
    private lateinit var editTravelFromTime: EditText
    private lateinit var editTravelToPlace: EditText
    private lateinit var editTravelToTime: EditText
    private lateinit var editOutboundInfo: EditText
    private lateinit var switchHasReturn: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var editReturnSection: LinearLayout
    private lateinit var editReturnDate: EditText
    private lateinit var editReturnFromPlace: EditText
    private lateinit var editReturnFromTime: EditText
    private lateinit var editReturnToPlace: EditText
    private lateinit var editReturnToTime: EditText

    // Booking.com-style date range UI
    private lateinit var editAccomDatesContainer: LinearLayout
    private lateinit var checkInButton: LinearLayout
    private lateinit var checkOutButton: LinearLayout
    private lateinit var checkInDateText: TextView
    private lateinit var checkOutDateText: TextView

    // Bottom bar
    private lateinit var viewModeActions: LinearLayout
    private lateinit var editModeActions: LinearLayout
    private lateinit var checkComplete: CheckBox
    private lateinit var btnEdit: Button
    private lateinit var btnDelete: Button
    private lateinit var btnArchive: Button
    private lateinit var btnRestore: Button
    private lateinit var btnCancel: Button
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_gig_detail)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.mutate()?.also {
            androidx.core.graphics.drawable.DrawableCompat.setTint(it, android.graphics.Color.WHITE)
            toolbar.navigationIcon = it
        }

        tourId = intent.getStringExtra(EXTRA_TOUR_ID) ?: run { finish(); return }
        gigIndex = intent.getIntExtra(EXTRA_GIG_INDEX, -1)
        artistId = intent.getIntExtra(EXTRA_ARTIST_ID, 0)

        // View fields
        completeBadge = findViewById(R.id.completeBadge)
        gigDateText = findViewById(R.id.gigDateText)
        cityVenueText = findViewById(R.id.cityVenueText)
        ticketsWithMeText = findViewById(R.id.ticketsWithMeText)
        whereTicketsText = findViewById(R.id.whereTicketsText)
        accommodationText = findViewById(R.id.accommodationText)
        whereAccomBoughtText = findViewById(R.id.whereAccomBoughtText)
        accomDatesText = findViewById(R.id.accomDatesText)
        costText = findViewById(R.id.costText)
        paidText = findViewById(R.id.paidText)
        accomCommentsText = findViewById(R.id.accomCommentsText)
        travelDetailsText = findViewById(R.id.travelDetailsText)
        travelDateText = findViewById(R.id.travelDateText)
        generalCommentsText = findViewById(R.id.generalCommentsText)
        travelFromText = findViewById(R.id.travelFromText)
        travelToText = findViewById(R.id.travelToText)
        outboundInfoText = findViewById(R.id.outboundInfoText)
        returnJourneySection = findViewById(R.id.returnJourneySection)
        returnDateText = findViewById(R.id.returnDateText)
        returnFromText = findViewById(R.id.returnFromText)
        returnToText = findViewById(R.id.returnToText)

        // Edit fields
        editDate = findViewById(R.id.editDate)
        editCityVenue = findViewById(R.id.editCityVenue)
        spinnerTicketsWithMe = findViewById(R.id.spinnerTicketsWithMe)
        editWhereTickets = findViewById(R.id.editWhereTickets)
        editAccommodation = findViewById(R.id.editAccommodation)
        editWhereAccomBought = findViewById(R.id.editWhereAccomBought)
        editAccomDates = findViewById(R.id.editAccomDates)
        editCost = findViewById(R.id.editCost)
        spinnerPaid = findViewById(R.id.spinnerPaid)
        editAccomComments = findViewById(R.id.editAccomComments)
        editTravelDetails = findViewById(R.id.editTravelDetails)
        editTravelDate = findViewById(R.id.editTravelDate)
        editGeneralComments = findViewById(R.id.editGeneralComments)
        editOutboundSection = findViewById(R.id.editOutboundSection)
        editTravelFromPlace = findViewById(R.id.editTravelFromPlace)
        editTravelFromTime = findViewById(R.id.editTravelFromTime)
        editTravelToPlace = findViewById(R.id.editTravelToPlace)
        editTravelToTime = findViewById(R.id.editTravelToTime)
        editOutboundInfo = findViewById(R.id.editOutboundInfo)
        switchHasReturn = findViewById(R.id.switchHasReturn)
        editReturnSection = findViewById(R.id.editReturnSection)
        editReturnDate = findViewById(R.id.editReturnDate)
        editReturnFromPlace = findViewById(R.id.editReturnFromPlace)
        editReturnFromTime = findViewById(R.id.editReturnFromTime)
        editReturnToPlace = findViewById(R.id.editReturnToPlace)
        editReturnToTime = findViewById(R.id.editReturnToTime)

        editAccomDatesContainer = findViewById(R.id.editAccomDatesContainer)
        checkInButton = findViewById(R.id.checkInButton)
        checkOutButton = findViewById(R.id.checkOutButton)
        checkInDateText = findViewById(R.id.checkInDateText)
        checkOutDateText = findViewById(R.id.checkOutDateText)

        // Bottom bar
        viewModeActions = findViewById(R.id.viewModeActions)
        editModeActions = findViewById(R.id.editModeActions)
        checkComplete = findViewById(R.id.checkComplete)
        btnEdit = findViewById(R.id.btnEdit)
        btnDelete = findViewById(R.id.btnDelete)
        btnArchive = findViewById(R.id.btnArchive)
        btnRestore = findViewById(R.id.btnRestore)
        btnCancel = findViewById(R.id.btnCancel)
        btnSave = findViewById(R.id.btnSave)

        val yesNoOptions = arrayOf("Yes", "No")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, yesNoOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTicketsWithMe.adapter = spinnerAdapter
        spinnerPaid.adapter = spinnerAdapter

        editDate.setOnClickListener { showDatePicker(editDate) }
        editTravelDate.setOnClickListener { showDatePicker(editTravelDate) }
        checkInButton.setOnClickListener { showAccomDateRangePicker() }
        checkOutButton.setOnClickListener { showAccomDateRangePicker() }
        editTravelFromTime.setOnClickListener { showTimePicker(editTravelFromTime) }
        editTravelToTime.setOnClickListener { showTimePicker(editTravelToTime) }
        editReturnDate.setOnClickListener { showDatePicker(editReturnDate) }
        editReturnFromTime.setOnClickListener { showTimePicker(editReturnFromTime) }
        editReturnToTime.setOnClickListener { showTimePicker(editReturnToTime) }
        switchHasReturn.setOnCheckedChangeListener { _, hasReturn ->
            editReturnSection.visibility = if (hasReturn) View.VISIBLE else View.GONE
        }

        loadGig()
        displayGig()

        btnEdit.setOnClickListener { enterEditMode() }
        btnCancel.setOnClickListener { exitEditMode() }
        btnSave.setOnClickListener { saveEdit() }
        btnDelete.setOnClickListener { showDeleteConfirmation() }
        btnArchive.setOnClickListener { archiveDate() }
        btnRestore.setOnClickListener { restoreDate() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (editModeActions.visibility == View.VISIBLE) exitEditMode() else finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadGig() {
        val tour = dataManager.getTour(tourId)
        val g = tour?.gigs?.getOrNull(gigIndex)
        if (g == null) { finish(); return }
        gig = g
    }

    private fun displayGig() {
        supportActionBar?.title = gig.cityVenue ?: "Date Details"
        supportActionBar?.subtitle = formatDisplayDate(gig.date)

        completeBadge.visibility = if (gig.isComplete == true) View.VISIBLE else View.GONE
        gigDateText.text = formatDisplayDate(gig.date)

        cityVenueText.text = (gig.cityVenue ?: "").ifEmpty { "N/A" }
        cityVenueText.paintFlags = cityVenueText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        cityVenueText.setOnClickListener {
            val v = cityVenueText.text.toString()
            if (v.isNotEmpty() && v != "N/A") openGoogleMaps(v)
        }

        highlightStatus(ticketsWithMeText, gig.ticketsWithMe ?: "")
        highlightInText(whereTicketsText, gig.whereTicketsAre ?: "")

        accommodationText.text = (gig.accommodation ?: "").ifEmpty { "N/A" }
        accommodationText.paintFlags = accommodationText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        accommodationText.setOnClickListener {
            val loc = accommodationText.text.toString()
            if (loc.isNotEmpty() && loc != "N/A") openGoogleMaps(loc)
        }

        whereAccomBoughtText.text = (gig.whereAccomBought ?: "").ifEmpty { "N/A" }
        accomDatesText.text = (gig.accomDates ?: "").ifEmpty { "N/A" }
        highlightInText(costText, gig.cost ?: "")
        highlightStatus(paidText, gig.paid ?: "")
        accomCommentsText.text = (gig.accomComments ?: "").ifEmpty { "N/A" }
        highlightInText(travelDetailsText, gig.travelDetails ?: "")
        travelDetailsText.visibility = if (gig.travelDetails.isNullOrEmpty()) View.GONE else View.VISIBLE
        travelDateText.text = (gig.travelDate ?: "").ifEmpty { "N/A" }
        generalCommentsText.text = (gig.generalComments ?: "").ifEmpty { "N/A" }
        val fromPlace = gig.travelFromPlace ?: ""
        val fromTime = gig.travelFromTime ?: ""
        travelFromText.text = if (fromPlace.isEmpty()) "N/A" else if (fromTime.isEmpty()) fromPlace else "$fromPlace at $fromTime"
        val toPlace = gig.travelToPlace ?: ""
        val toTime = gig.travelToTime ?: ""
        travelToText.text = if (toPlace.isEmpty()) "N/A" else if (toTime.isEmpty()) toPlace else "$toPlace at $toTime"
        outboundInfoText.text = gig.outboundInfo ?: ""
        outboundInfoText.visibility = if (gig.outboundInfo.isNullOrEmpty()) View.GONE else View.VISIBLE
        val hasReturn = gig.hasReturnJourney != false
        returnJourneySection.visibility = if (hasReturn) View.VISIBLE else View.GONE
        returnDateText.text = (gig.returnDate ?: "").ifEmpty { "N/A" }
        val retFromPlace = gig.returnFromPlace ?: ""
        val retFromTime = gig.returnFromTime ?: ""
        returnFromText.text = if (retFromPlace.isEmpty()) "N/A" else if (retFromTime.isEmpty()) retFromPlace else "$retFromPlace at $retFromTime"
        val retToPlace = gig.returnToPlace ?: ""
        val retToTime = gig.returnToTime ?: ""
        returnToText.text = if (retToPlace.isEmpty()) "N/A" else if (retToTime.isEmpty()) retToPlace else "$retToPlace at $retToTime"

        checkComplete.setOnCheckedChangeListener(null)
        checkComplete.isChecked = gig.isComplete == true
        checkComplete.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && gig.isComplete != true) {
                AlertDialog.Builder(this)
                    .setTitle("Mark as Done")
                    .setMessage("Marking as done will also archive this date. Continue?")
                    .setPositiveButton("Yes") { _, _ ->
                        dataManager.updateGigInTour(tourId, gigIndex, gig.copy(isComplete = true, isArchived = true))
                        finish()
                    }
                    .setNegativeButton("No") { _, _ ->
                        checkComplete.setOnCheckedChangeListener(null)
                        checkComplete.isChecked = false
                        checkComplete.setOnCheckedChangeListener { _, c -> if (c) showMarkDoneConfirmation() }
                    }
                    .show()
            } else if (!isChecked && gig.isComplete == true) {
                dataManager.updateGigInTour(tourId, gigIndex, gig.copy(isComplete = false))
                loadGig(); displayGig()
            }
        }

        if (gig.isArchived == true) {
            btnArchive.visibility = View.GONE
            btnRestore.visibility = View.VISIBLE
        } else {
            btnArchive.visibility = View.VISIBLE
            btnRestore.visibility = View.GONE
        }
    }

    @android.annotation.SuppressLint("SetTextI18n")
    private fun enterEditMode() {
        val yesNoOptions = arrayOf("Yes", "No")
        editDate.setText(gig.date ?: "")
        editCityVenue.setText(gig.cityVenue ?: "")
        yesNoOptions.indexOf(gig.ticketsWithMe ?: "").let { if (it >= 0) spinnerTicketsWithMe.setSelection(it) }
        editWhereTickets.setText(gig.whereTicketsAre ?: "")
        editAccommodation.setText(gig.accommodation ?: "")
        editWhereAccomBought.setText(gig.whereAccomBought ?: "")
        editAccomDates.setText(gig.accomDates ?: "")
        // sync check-in/check-out display labels
        val existingDates = gig.accomDates ?: ""
        if (existingDates.contains("–") || existingDates.contains("-")) {
            val parts = existingDates.split("–", "-").map { it.trim() }
            checkInDateText.text = parts.getOrNull(0)?.ifEmpty { "Add date" } ?: "Add date"
            checkOutDateText.text = parts.getOrNull(1)?.ifEmpty { "Add date" } ?: "Add date"
        } else if (existingDates.isNotEmpty()) {
            checkInDateText.text = existingDates
            checkOutDateText.text = "Add date"
        } else {
            checkInDateText.text = "Add date"
            checkOutDateText.text = "Add date"
        }
        editCost.setText(gig.cost ?: "")
        yesNoOptions.indexOf(gig.paid ?: "").let { if (it >= 0) spinnerPaid.setSelection(it) }
        editAccomComments.setText(gig.accomComments ?: "")
        editTravelDetails.setText(gig.travelDetails ?: "")
        editTravelDate.setText(gig.travelDate ?: "")
        editGeneralComments.setText(gig.generalComments ?: "")
        editTravelFromPlace.setText(gig.travelFromPlace ?: "")
        editTravelFromTime.setText(gig.travelFromTime ?: "")
        editTravelToPlace.setText(gig.travelToPlace ?: "")
        editTravelToTime.setText(gig.travelToTime ?: "")
        editOutboundInfo.setText(gig.outboundInfo ?: "")
        switchHasReturn.isChecked = gig.hasReturnJourney != false
        editReturnDate.setText(gig.returnDate ?: "")
        editReturnFromPlace.setText(gig.returnFromPlace ?: "")
        editReturnFromTime.setText(gig.returnFromTime ?: "")
        editReturnToPlace.setText(gig.returnToPlace ?: "")
        editReturnToTime.setText(gig.returnToTime ?: "")
        editReturnSection.visibility = if (gig.hasReturnJourney != false) View.VISIBLE else View.GONE

        gigDateText.visibility = View.GONE;           editDate.visibility = View.VISIBLE
        cityVenueText.visibility = View.GONE;         editCityVenue.visibility = View.VISIBLE
        ticketsWithMeText.visibility = View.GONE;     spinnerTicketsWithMe.visibility = View.VISIBLE
        whereTicketsText.visibility = View.GONE;      editWhereTickets.visibility = View.VISIBLE
        accommodationText.visibility = View.GONE;     editAccommodation.visibility = View.VISIBLE
        whereAccomBoughtText.visibility = View.GONE;  editWhereAccomBought.visibility = View.VISIBLE
        accomDatesText.visibility = View.GONE;        editAccomDatesContainer.visibility = View.VISIBLE
        costText.visibility = View.GONE;              editCost.visibility = View.VISIBLE
        paidText.visibility = View.GONE;              spinnerPaid.visibility = View.VISIBLE
        accomCommentsText.visibility = View.GONE;     editAccomComments.visibility = View.VISIBLE
        travelDateText.visibility = View.GONE;        editTravelDate.visibility = View.VISIBLE
        generalCommentsText.visibility = View.GONE;   editGeneralComments.visibility = View.VISIBLE
        travelFromText.visibility = View.GONE;   editOutboundSection.visibility = View.VISIBLE
        travelToText.visibility = View.GONE;     switchHasReturn.visibility = View.VISIBLE
        outboundInfoText.visibility = View.GONE; editOutboundInfo.visibility = View.VISIBLE
        returnJourneySection.visibility = View.GONE
        // editReturnSection and editTravelDetails visibility controlled by switchHasReturn state (set above)

        viewModeActions.visibility = View.GONE
        editModeActions.visibility = View.VISIBLE
        supportActionBar?.subtitle = "Editing..."
    }

    private fun exitEditMode() {
        hideKeyboard()
        gigDateText.visibility = View.VISIBLE;        editDate.visibility = View.GONE
        cityVenueText.visibility = View.VISIBLE;      editCityVenue.visibility = View.GONE
        ticketsWithMeText.visibility = View.VISIBLE;  spinnerTicketsWithMe.visibility = View.GONE
        whereTicketsText.visibility = View.VISIBLE;   editWhereTickets.visibility = View.GONE
        accommodationText.visibility = View.VISIBLE;  editAccommodation.visibility = View.GONE
        whereAccomBoughtText.visibility = View.VISIBLE; editWhereAccomBought.visibility = View.GONE
        accomDatesText.visibility = View.VISIBLE;     editAccomDatesContainer.visibility = View.GONE
        costText.visibility = View.VISIBLE;           editCost.visibility = View.GONE
        paidText.visibility = View.VISIBLE;           spinnerPaid.visibility = View.GONE
        accomCommentsText.visibility = View.VISIBLE;  editAccomComments.visibility = View.GONE
        travelDateText.visibility = View.VISIBLE;     editTravelDate.visibility = View.GONE
        generalCommentsText.visibility = View.VISIBLE; editGeneralComments.visibility = View.GONE
        travelFromText.visibility = View.VISIBLE;    editOutboundSection.visibility = View.GONE
        travelToText.visibility = View.VISIBLE;      switchHasReturn.visibility = View.GONE
        editOutboundInfo.visibility = View.GONE
        returnJourneySection.visibility = if (gig.hasReturnJourney != false) View.VISIBLE else View.GONE
        editReturnSection.visibility = View.GONE

        editModeActions.visibility = View.GONE
        viewModeActions.visibility = View.VISIBLE
        displayGig()
    }

    private fun saveEdit() {
        val newGig = Gig(
            date = editDate.text.toString(),
            cityVenue = editCityVenue.text.toString(),
            ticketsWithMe = spinnerTicketsWithMe.selectedItem.toString(),
            whereTicketsAre = editWhereTickets.text.toString(),
            accommodation = editAccommodation.text.toString(),
            whereAccomBought = editWhereAccomBought.text.toString(),
            accomDates = editAccomDates.text.toString(),
            cost = editCost.text.toString(),
            paid = spinnerPaid.selectedItem.toString(),
            accomComments = editAccomComments.text.toString(),
            travelDetails = editTravelDetails.text.toString(),
            travelDate = editTravelDate.text.toString(),
            travelFromPlace = editTravelFromPlace.text.toString(),
            travelFromTime = editTravelFromTime.text.toString(),
            travelToPlace = editTravelToPlace.text.toString(),
            travelToTime = editTravelToTime.text.toString(),
            outboundInfo = editOutboundInfo.text.toString().ifEmpty { null },
            hasReturnJourney = switchHasReturn.isChecked,
            returnDate = if (switchHasReturn.isChecked) editReturnDate.text.toString() else null,
            returnFromPlace = if (switchHasReturn.isChecked) editReturnFromPlace.text.toString() else null,
            returnFromTime = if (switchHasReturn.isChecked) editReturnFromTime.text.toString() else null,
            returnToPlace = if (switchHasReturn.isChecked) editReturnToPlace.text.toString() else null,
            returnToTime = if (switchHasReturn.isChecked) editReturnToTime.text.toString() else null,
            generalComments = editGeneralComments.text.toString(),
            isComplete = gig.isComplete,
            isArchived = gig.isArchived
        )
        if (newGig.cityVenue.isNullOrEmpty()) {
            Toast.makeText(this, "City & Venue is required", Toast.LENGTH_SHORT).show()
            return
        }
        if (dataManager.updateGigInTour(tourId, gigIndex, newGig)) {
            loadGig()
            Toast.makeText(this, "Date updated", Toast.LENGTH_SHORT).show()
            exitEditMode()
        } else {
            Toast.makeText(this, "Error saving date", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    @SuppressLint("DefaultLocale")
    private fun showDatePicker(field: EditText) {
        val calendar = Calendar.getInstance()
        val existing = field.text.toString().trim()
        if (existing.isNotEmpty()) {
            listOf(
                SimpleDateFormat("EEEE, d/M/yyyy", Locale.ENGLISH),
                SimpleDateFormat("d/M/yyyy", Locale.ENGLISH),
                SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH),
                SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH)
            ).forEach { fmt ->
                try { fmt.parse(existing)?.let { calendar.time = it; return@forEach } } catch (e: Exception) { }
            }
        }
        DatePickerDialog(this, { _, y, m, d ->
            field.setText(String.format("%d/%d/%d", d, m + 1, y))
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    @SuppressLint("DefaultLocale")
    private fun showTimePicker(field: EditText) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            field.setText(String.format("%02d:%02d", hour, minute))
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    @SuppressLint("DefaultLocale")
    private fun showAccomDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select accommodation dates")
            .build()
        picker.show(supportFragmentManager, "accom_date_range")
        picker.addOnPositiveButtonClickListener { selection ->
            val formatter = SimpleDateFormat("d/M/yyyy", Locale.ENGLISH)
            val checkIn = formatter.format(selection.first)
            val checkOut = formatter.format(selection.second)
            checkInDateText.text = checkIn
            checkOutDateText.text = checkOut
            editAccomDates.setText(String.format("%s \u2013 %s", checkIn, checkOut))
        }
    }

    private fun showMarkDoneConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Mark as Done")
            .setMessage("Marking as done will also archive this date. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                dataManager.updateGigInTour(tourId, gigIndex, gig.copy(isComplete = true, isArchived = true))
                finish()
            }
            .setNegativeButton("No") { _, _ ->
                checkComplete.setOnCheckedChangeListener(null)
                checkComplete.isChecked = false
                checkComplete.setOnCheckedChangeListener { _, isChecked -> if (isChecked) showMarkDoneConfirmation() }
            }
            .show()
    }

    private fun archiveDate() {
        AlertDialog.Builder(this)
            .setTitle("Archive Date")
            .setMessage("Move \"${gig.cityVenue}\" to archived dates?")
            .setPositiveButton("Archive") { _, _ ->
                dataManager.updateGigInTour(tourId, gigIndex, gig.copy(isArchived = true))
                Toast.makeText(this, "Date archived", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restoreDate() {
        dataManager.updateGigInTour(tourId, gigIndex, gig.copy(isArchived = false, isComplete = false))
        Toast.makeText(this, "Date restored", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Date")
            .setMessage("Are you sure you want to delete ${gig.cityVenue}?")
            .setPositiveButton("Delete") { _, _ ->
                if (dataManager.deleteGigFromTour(tourId, gigIndex)) {
                    Toast.makeText(this, "Date deleted", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Error deleting date", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openGoogleMaps(address: String) {
        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(address)}")))
        }
    }

    private fun formatDisplayDate(dateStr: String?): String {
        val input = dateStr ?: return "N/A"
        return try {
            val inputFormats = arrayOf(
                SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH),
                SimpleDateFormat("d/M/yyyy", Locale.ENGLISH)
            )
            var date: java.util.Date? = null
            for (format in inputFormats) {
                try { date = format.parse(input); if (date != null) break } catch (e: Exception) { }
            }
            if (date != null) SimpleDateFormat("EEEE, d/M/yyyy", Locale.ENGLISH).format(date) else input
        } catch (e: Exception) { input }
    }

    private fun highlightStatus(textView: TextView, value: String?) {
        val input = value ?: ""
        textView.text = input.ifEmpty { "N/A" }
        when {
            input.equals("Yes", ignoreCase = true) -> {
                textView.setBackgroundColor(Color.parseColor("#4CAF50"))
                textView.setTextColor(Color.WHITE)
                textView.setPadding(16, 8, 16, 8)
            }
            input.equals("No", ignoreCase = true) -> {
                textView.setBackgroundColor(Color.parseColor("#F44336"))
                textView.setTextColor(Color.WHITE)
                textView.setPadding(16, 8, 16, 8)
            }
            else -> {
                textView.setBackgroundColor(Color.TRANSPARENT)
                textView.setTextColor(Color.BLACK)
                textView.setPadding(0, 0, 0, 0)
            }
        }
    }

    @android.annotation.SuppressLint("SetTextI18n")
    private fun highlightInText(textView: TextView, text: String?) {
        val input = text ?: ""
        if (input.isEmpty()) { textView.text = "N/A"; return }
        val builder = SpannableStringBuilder(input)
        val yesPattern = Pattern.compile("\\bYes\\b", Pattern.CASE_INSENSITIVE)
        val yesMatcher = yesPattern.matcher(input)
        while (yesMatcher.find()) {
            builder.setSpan(BackgroundColorSpan(Color.parseColor("#4CAF50")), yesMatcher.start(), yesMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(ForegroundColorSpan(Color.WHITE), yesMatcher.start(), yesMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val noPattern = Pattern.compile("\\bNo\\b", Pattern.CASE_INSENSITIVE)
        val noMatcher = noPattern.matcher(input)
        while (noMatcher.find()) {
            builder.setSpan(BackgroundColorSpan(Color.parseColor("#F44336")), noMatcher.start(), noMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(ForegroundColorSpan(Color.WHITE), noMatcher.start(), noMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textView.text = builder
    }
}
