package com.suede.gigmanager

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TourDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TOUR_ID = "extra_tour_id"
        private const val EXTRA_ARTIST_ID = "extra_artist_id"

        fun start(context: Context, tourId: String, artistId: Int) {
            context.startActivity(Intent(context, TourDetailActivity::class.java).apply {
                putExtra(EXTRA_TOUR_ID, tourId)
                putExtra(EXTRA_ARTIST_ID, artistId)
            })
        }
    }

    private lateinit var tourId: String
    private var artistId: Int = 0
    private lateinit var dataManager: GigDataManager

    // List views
    private lateinit var listContainer: LinearLayout
    private lateinit var recyclerActive: RecyclerView
    private lateinit var recyclerArchived: RecyclerView
    private lateinit var tvNoActiveDates: TextView
    private lateinit var headerArchived: LinearLayout
    private lateinit var tvArchivedHeader: TextView
    private lateinit var tvArchivedChevron: TextView
    private lateinit var btnAddDate: FloatingActionButton

    // Add form views
    private lateinit var addFormContainer: LinearLayout
    private lateinit var addFormActions: LinearLayout
    private lateinit var addEditDate: EditText
    private lateinit var addEditCityVenue: EditText
    private lateinit var addSpinnerTicketsWithMe: Spinner
    private lateinit var addEditWhereTickets: EditText
    private lateinit var addEditAccommodation: EditText
    private lateinit var addEditWhereAccomBought: EditText
    private lateinit var addEditAccomDates: EditText
    private lateinit var addEditCost: EditText
    private lateinit var addSpinnerPaid: Spinner
    private lateinit var addEditAccomComments: EditText
    private lateinit var addEditTravelDetails: EditText
    private lateinit var btnCancelAdd: Button
    private lateinit var btnSaveAdd: Button

    private var archivedExpanded = false
    private var inAddMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_tour_detail)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        tourId = intent.getStringExtra(EXTRA_TOUR_ID) ?: run { finish(); return }
        artistId = intent.getIntExtra(EXTRA_ARTIST_ID, 0)
        dataManager = GigDataManager(this, artistId)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // List views
        listContainer = findViewById(R.id.listContainer)
        recyclerActive = findViewById(R.id.recyclerActiveDates)
        recyclerArchived = findViewById(R.id.recyclerArchivedDates)
        tvNoActiveDates = findViewById(R.id.tvNoActiveDates)
        headerArchived = findViewById(R.id.headerArchived)
        tvArchivedHeader = findViewById(R.id.tvArchivedHeader)
        tvArchivedChevron = findViewById(R.id.tvArchivedChevron)
        btnAddDate = findViewById(R.id.btnAddDate)

        // Add form views
        addFormContainer = findViewById(R.id.addFormContainer)
        addFormActions = findViewById(R.id.addFormActions)
        addEditDate = findViewById(R.id.addEditDate)
        addEditCityVenue = findViewById(R.id.addEditCityVenue)
        addSpinnerTicketsWithMe = findViewById(R.id.addSpinnerTicketsWithMe)
        addEditWhereTickets = findViewById(R.id.addEditWhereTickets)
        addEditAccommodation = findViewById(R.id.addEditAccommodation)
        addEditWhereAccomBought = findViewById(R.id.addEditWhereAccomBought)
        addEditAccomDates = findViewById(R.id.addEditAccomDates)
        addEditCost = findViewById(R.id.addEditCost)
        addSpinnerPaid = findViewById(R.id.addSpinnerPaid)
        addEditAccomComments = findViewById(R.id.addEditAccomComments)
        addEditTravelDetails = findViewById(R.id.addEditTravelDetails)
        btnCancelAdd = findViewById(R.id.btnCancelAdd)
        btnSaveAdd = findViewById(R.id.btnSaveAdd)

        recyclerActive.layoutManager = LinearLayoutManager(this)
        recyclerArchived.layoutManager = LinearLayoutManager(this)

        val yesNoOptions = arrayOf("Yes", "No")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, yesNoOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        addSpinnerTicketsWithMe.adapter = spinnerAdapter
        addSpinnerPaid.adapter = spinnerAdapter

        addEditDate.setOnClickListener { showAddDatePicker() }
        addEditAccomDates.setOnClickListener { showAddAccomDatePicker() }

        headerArchived.setOnClickListener { toggleArchivedSection() }
        btnAddDate.setOnClickListener { enterAddMode() }
        btnCancelAdd.setOnClickListener { exitAddMode() }
        btnSaveAdd.setOnClickListener { saveNewGig() }
    }

    override fun onResume() {
        super.onResume()
        refreshPage()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (inAddMode) exitAddMode() else finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (inAddMode) exitAddMode() else super.onBackPressed()
    }

    private fun enterAddMode() {
        inAddMode = true
        // Clear all fields
        addEditDate.setText("")
        addEditCityVenue.setText("")
        addSpinnerTicketsWithMe.setSelection(0)
        addEditWhereTickets.setText("")
        addEditAccommodation.setText("")
        addEditWhereAccomBought.setText("")
        addEditAccomDates.setText("")
        addEditCost.setText("")
        addSpinnerPaid.setSelection(0)
        addEditAccomComments.setText("")
        addEditTravelDetails.setText("")

        listContainer.visibility = View.GONE
        btnAddDate.visibility = View.GONE
        addFormContainer.visibility = View.VISIBLE
        addFormActions.visibility = View.VISIBLE
        supportActionBar?.subtitle = "Add New Date"
    }

    private fun exitAddMode() {
        inAddMode = false
        hideKeyboard()
        addFormContainer.visibility = View.GONE
        addFormActions.visibility = View.GONE
        listContainer.visibility = View.VISIBLE
        btnAddDate.visibility = View.VISIBLE
        supportActionBar?.subtitle = null
    }

    private fun saveNewGig() {
        val newGig = Gig(
            date = addEditDate.text.toString(),
            cityVenue = addEditCityVenue.text.toString(),
            ticketsWithMe = addSpinnerTicketsWithMe.selectedItem.toString(),
            whereTicketsAre = addEditWhereTickets.text.toString(),
            accommodation = addEditAccommodation.text.toString(),
            whereAccomBought = addEditWhereAccomBought.text.toString(),
            accomDates = addEditAccomDates.text.toString(),
            cost = addEditCost.text.toString(),
            paid = addSpinnerPaid.selectedItem.toString(),
            accomComments = addEditAccomComments.text.toString(),
            travelDetails = addEditTravelDetails.text.toString(),
            isComplete = false,
            isArchived = false
        )
        if (newGig.cityVenue.isNullOrEmpty()) {
            Toast.makeText(this, "City & Venue is required", Toast.LENGTH_SHORT).show()
            return
        }
        if (dataManager.addGigToTour(tourId, newGig)) {
            Toast.makeText(this, "Date added", Toast.LENGTH_SHORT).show()
            exitAddMode()
            refreshPage()
        } else {
            Toast.makeText(this, "Error saving date", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    @SuppressLint("DefaultLocale")
    private fun showAddDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            addEditDate.setText(String.format("%d/%d/%d", d, m + 1, y))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    @SuppressLint("DefaultLocale")
    private fun showAddAccomDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y1, m1, d1 ->
            val checkIn = String.format("%d/%d/%d", d1, m1 + 1, y1)
            DatePickerDialog(this, { _, y2, m2, d2 ->
                addEditAccomDates.setText("$checkIn – ${String.format("%d/%d/%d", d2, m2 + 1, y2)}")
            }, y1, m1, d1).also { it.setTitle("Check-out date") }.show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            .also { it.setTitle("Check-in date") }.show()
    }

    private fun refreshPage() {
        val tour = dataManager.getTour(tourId) ?: run { finish(); return }
        supportActionBar?.title = tour.name

        val activeWithIndex = tour.gigs.mapIndexedNotNull { i, g ->
            if (g.isArchived != true) Pair(i, g) else null
        }
        val archivedWithIndex = tour.gigs.mapIndexedNotNull { i, g ->
            if (g.isArchived == true) Pair(i, g) else null
        }

        if (activeWithIndex.isEmpty()) {
            recyclerActive.visibility = View.GONE
            tvNoActiveDates.visibility = View.VISIBLE
        } else {
            tvNoActiveDates.visibility = View.GONE
            recyclerActive.visibility = View.VISIBLE
            recyclerActive.adapter = GigPillAdapter(activeWithIndex)
        }

        if (archivedWithIndex.isEmpty()) {
            headerArchived.visibility = View.GONE
            recyclerArchived.visibility = View.GONE
        } else {
            headerArchived.visibility = View.VISIBLE
            tvArchivedHeader.text = "Archived Dates (${archivedWithIndex.size})"
            recyclerArchived.adapter = GigPillAdapter(archivedWithIndex)
            updateArchivedChevron()
        }
    }

    private fun toggleArchivedSection() {
        archivedExpanded = !archivedExpanded
        recyclerArchived.visibility = if (archivedExpanded) View.VISIBLE else View.GONE
        updateArchivedChevron()
    }

    private fun updateArchivedChevron() {
        tvArchivedChevron.text = if (archivedExpanded) "▼" else "▲"
    }

    // ---- Adapter --------------------------------------------------------

    private inner class GigPillAdapter(private val items: List<Pair<Int, Gig>>) :
        RecyclerView.Adapter<GigPillAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView.findViewById(R.id.pillCard)
            val dateText: TextView = itemView.findViewById(R.id.pillDate)
            val cityText: TextView = itemView.findViewById(R.id.pillCity)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_archive_gig_pill, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (gigIndex, gig) = items[position]
            holder.dateText.text = formatDisplayDate(gig.date)
            holder.cityText.text = gig.cityVenue ?: "Unknown"
            val bg = when {
                gig.isArchived == true -> Color.parseColor("#EEEEEE")
                gig.isComplete == true -> Color.parseColor("#C8E6C9")
                else -> Color.WHITE
            }
            holder.card.setCardBackgroundColor(bg)
            holder.card.setOnClickListener {
                GigDetailActivity.start(this@TourDetailActivity, tourId, gigIndex, artistId)
            }
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
            for (fmt in inputFormats) {
                try { date = fmt.parse(input); if (date != null) break } catch (e: Exception) { }
            }
            if (date != null) SimpleDateFormat("EEEE, d/M/yyyy", Locale.ENGLISH).format(date) else input
        } catch (e: Exception) { input }
    }
}
