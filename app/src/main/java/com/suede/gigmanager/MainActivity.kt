package com.suede.gigmanager

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var venueSpinner: Spinner
    private lateinit var detailsContainer: LinearLayout
    private lateinit var actionButtonsContainer: LinearLayout
    private lateinit var btnAddGig: Button
    private lateinit var btnEditGig: Button
    private lateinit var btnDeleteGig: Button
    private lateinit var btnArchiveTour: Button
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var archiveDrawerList: ListView
    private lateinit var archivesEmptyText: TextView
    
    private lateinit var checkComplete: CheckBox
    private lateinit var dateText: TextView
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

    private lateinit var dataManager: GigDataManager
    private lateinit var syncService: GigSyncService
    private var gigs: MutableList<Gig> = mutableListOf()
    private var selectedGigIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.purple_700)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        setContentView(R.layout.activity_main)

        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Initialize data manager
        dataManager = GigDataManager(this)
        syncService = GigSyncService(this)

        // Initialize views
        initializeViews()

        // Load gigs data
        loadGigsData()

        // Display current tour name
        supportActionBar?.title = dataManager.getCurrentTourName()

        // Setup spinner
        setupVenueSpinner()
        
        // Setup button listeners
        setupButtonListeners()
    }

    private fun initializeViews() {
        venueSpinner = findViewById(R.id.venueSpinner)
        detailsContainer = findViewById(R.id.detailsContainer)
        actionButtonsContainer = findViewById(R.id.actionButtonsContainer)
        btnAddGig = findViewById(R.id.btnAddGig)
        btnEditGig = findViewById(R.id.btnEditGig)
        btnDeleteGig = findViewById(R.id.btnDeleteGig)
        btnArchiveTour = findViewById(R.id.btnArchiveTour)
        drawerLayout = findViewById(R.id.drawerLayout)
        archiveDrawerList = findViewById(R.id.archiveDrawerList)
        archivesEmptyText = findViewById(R.id.archivesEmptyText)
        
        checkComplete = findViewById(R.id.checkComplete)
        dateText = findViewById(R.id.dateText)
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

        // Add underlines to make them look like links
        cityVenueText.paintFlags = cityVenueText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        accommodationText.paintFlags = accommodationText.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        cityVenueText.setOnClickListener {
            val venue = cityVenueText.text.toString()
            if (venue.isNotEmpty() && venue != "N/A") {
                openGoogleMaps(venue)
            }
        }

        accommodationText.setOnClickListener {
            val location = accommodationText.text.toString()
            if (location.isNotEmpty() && location != "N/A") {
                openGoogleMaps(location)
            }
        }

        checkComplete.setOnCheckedChangeListener { _, isChecked ->
            if (selectedGigIndex >= 0) {
                val gig = gigs[selectedGigIndex]
                if (gig.isComplete != isChecked) {
                    val updatedGig = gig.copy(isComplete = isChecked)
                    if (dataManager.updateGig(selectedGigIndex, updatedGig)) {
                        // Reload gigs since update may reorder the list
                        loadGigsData()
                        updateSpinner()
                        // Find the new index of the updated gig and select it
                        val newIndex = gigs.indexOfFirst { it == updatedGig }.takeIf { it >= 0 } ?: 0
                        selectedGigIndex = newIndex
                        venueSpinner.setSelection(newIndex + 1)
                    }
                }
            }
        }
    }

    private fun openGoogleMaps(address: String) {
        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            // Fallback to web browser if Maps app is not installed
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(address)}"))
            startActivity(webIntent)
        }
    }

    private fun loadGigsData() {
        gigs = dataManager.loadGigs().toMutableList()
    }

    private fun setupVenueSpinner() {
        updateSpinner()

        venueSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position == 0) {
                    detailsContainer.visibility = View.GONE
                    actionButtonsContainer.visibility = View.GONE
                    selectedGigIndex = -1
                } else {
                    selectedGigIndex = position - 1
                    val selectedGig = gigs[selectedGigIndex]
                    displayGigDetails(selectedGig)
                    actionButtonsContainer.visibility = View.VISIBLE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                detailsContainer.visibility = View.GONE
                actionButtonsContainer.visibility = View.GONE
                selectedGigIndex = -1
            }
        }
    }

    private fun formatDisplayDate(dateStr: String?): String {
        val input = dateStr ?: return "N/A"
        return try {
            // Handle "Friday, 30 January 2026" or "30/01/2026"
            val inputFormats = arrayOf(
                SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH),
                SimpleDateFormat("d/M/yyyy", Locale.ENGLISH)
            )
            
            var date: java.util.Date? = null
            for (format in inputFormats) {
                try {
                    date = format.parse(input)
                    if (date != null) break
                } catch (e: Exception) {}
            }
            
            if (date != null) {
                val outputFormat = SimpleDateFormat("EEEE, d/M/yyyy", Locale.ENGLISH)
                outputFormat.format(date)
            } else {
                input
            }
        } catch (e: Exception) {
            input
        }
    }

    private fun updateSpinner() {
        val spinnerItems = mutableListOf("Select a city...")
        
        spinnerItems.addAll(gigs.map { 
            val city = it.cityVenue?.split(" ")?.firstOrNull() ?: it.cityVenue ?: "Unknown"
            "${formatDisplayDate(it.date)} - $city"
        })

        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            spinnerItems
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                // We don't set background color in getView to keep the drop-down chevron visible
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                if (position > 0 && gigs[position - 1].isComplete == true) {
                    view.setBackgroundColor(Color.parseColor("#C8E6C9")) // Light Green
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT)
                }
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        venueSpinner.adapter = adapter
    }

    private fun setupButtonListeners() {
        btnAddGig.setOnClickListener {
            showEditDialog(null, -1)
        }

        btnEditGig.setOnClickListener {
            if (selectedGigIndex >= 0) {
                showEditDialog(gigs[selectedGigIndex], selectedGigIndex)
            }
        }

        btnDeleteGig.setOnClickListener {
            if (selectedGigIndex >= 0) {
                showDeleteConfirmation()
            }
        }

        btnArchiveTour.setOnClickListener {
            showArchiveTourDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_sync_account)?.title =
            if (syncService.isLoggedIn()) "Sync Account" else "Login / Register"
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_archives) {
            openArchivesDrawer()
            return true
        }
        if (item.itemId == R.id.action_sync_account) {
            AccountActivity.start(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openArchivesDrawer() {
        val archives = dataManager.loadArchives()
        if (archives.isEmpty()) {
            archivesEmptyText.visibility = View.VISIBLE
            archiveDrawerList.visibility = View.GONE
        } else {
            archivesEmptyText.visibility = View.GONE
            archiveDrawerList.visibility = View.VISIBLE
            val items = archives.map { archive ->
                val dateRange = run {
                    val formats = listOf(
                        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH),
                        SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH),
                        SimpleDateFormat("EEEE, d/M/yyyy", Locale.ENGLISH),
                        SimpleDateFormat("d/M/yyyy", Locale.ENGLISH)
                    )
                    val short = SimpleDateFormat("d/M/yyyy", Locale.ENGLISH)
                    fun parseDate(s: String): java.util.Date? {
                        for (fmt in formats) { try { return fmt.parse(s) } catch (e: Exception) { } }
                        return null
                    }
                    val dates = archive.gigs.mapNotNull { parseDate(it.date ?: "") }
                    if (dates.isEmpty()) archive.archivedDate
                    else "${short.format(dates.min())} – ${short.format(dates.max())}"
                }
                "${archive.tourName}\n$dateRange · ${archive.gigs.size} gig${if (archive.gigs.size != 1) "s" else ""}"
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_2,
                android.R.id.text1, items)
            archiveDrawerList.adapter = adapter
            archiveDrawerList.setOnItemClickListener { _, _, position, _ ->
                drawerLayout.closeDrawer(GravityCompat.END)
                ArchiveViewActivity.start(this, archives[position])
            }
        }
        drawerLayout.openDrawer(GravityCompat.END)
    }

    @SuppressLint("DefaultLocale")
    private fun showEditDialog(gig: Gig?, index: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_gig, null)
        
        val editDate = dialogView.findViewById<EditText>(R.id.editDate)
        val editCityVenue = dialogView.findViewById<EditText>(R.id.editCityVenue)
        val spinnerTicketsWithMe = dialogView.findViewById<Spinner>(R.id.spinnerTicketsWithMe)
        val editWhereTickets = dialogView.findViewById<EditText>(R.id.editWhereTickets)
        val editAccommodation = dialogView.findViewById<EditText>(R.id.editAccommodation)
        val editWhereAccomBought = dialogView.findViewById<EditText>(R.id.editWhereAccomBought)
        val editAccomDates = dialogView.findViewById<EditText>(R.id.editAccomDates)
        val editCost = dialogView.findViewById<EditText>(R.id.editCost)
        val spinnerPaid = dialogView.findViewById<Spinner>(R.id.spinnerPaid)
        val editAccomComments = dialogView.findViewById<EditText>(R.id.editAccomComments)
        val editTravelDetails = dialogView.findViewById<EditText>(R.id.editTravelDetails)

        val yesNoOptions = arrayOf("Yes", "No")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, yesNoOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        
        spinnerTicketsWithMe.adapter = spinnerAdapter
        spinnerPaid.adapter = spinnerAdapter

        editDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val dateString = String.format("%d/%d/%d", selectedDay, selectedMonth + 1, selectedYear)
                editDate.setText(dateString)
            }, year, month, day).show()
        }

        // If editing existing gig, populate fields
        gig?.let {
            editDate.setText(it.date ?: "")
            editCityVenue.setText(it.cityVenue ?: "")
            
            val ticketsIndex = yesNoOptions.indexOf(it.ticketsWithMe ?: "")
            if (ticketsIndex >= 0) spinnerTicketsWithMe.setSelection(ticketsIndex)
            
            editWhereTickets.setText(it.whereTicketsAre ?: "")
            editAccommodation.setText(it.accommodation ?: "")
            editWhereAccomBought.setText(it.whereAccomBought ?: "")
            editAccomDates.setText(it.accomDates ?: "")
            editCost.setText(it.cost ?: "")
            
            val paidIndex = yesNoOptions.indexOf(it.paid ?: "")
            if (paidIndex >= 0) spinnerPaid.setSelection(paidIndex)
            
            editAccomComments.setText(it.accomComments ?: "")
            editTravelDetails.setText(it.travelDetails ?: "")
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnSave).setOnClickListener {
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
                isComplete = gig?.isComplete ?: false
            )

            if (newGig.cityVenue.isNullOrEmpty()) {
                Toast.makeText(this, "City & Venue is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val success = if (index >= 0) {
                // Update existing gig
                dataManager.updateGig(index, newGig)
            } else {
                // Add new gig
                dataManager.addGig(newGig)
            }

            if (success) {
                loadGigsData()
                updateSpinner()
                
                if (index >= 0) {
                        // After updating, the list may have been re-sorted. Find the actual index.
                        val updatedIndex = gigs.indexOfFirst { it == newGig }.takeIf { it >= 0 } ?: 0
                        venueSpinner.setSelection(updatedIndex + 1)
                        Toast.makeText(this, "Gig updated", Toast.LENGTH_SHORT).show()
                } else {
                        // After adding, the new gig may not be the last item due to sorting.
                        val addedIndex = gigs.indexOfFirst { it == newGig }.takeIf { it >= 0 } ?: (gigs.size - 1)
                        venueSpinner.setSelection(addedIndex + 1)
                        Toast.makeText(this, "Gig added", Toast.LENGTH_SHORT).show()
                }
                
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Error saving gig", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showArchiveTourDialog() {
        if (gigs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Archive Tour")
                .setMessage("There are no gigs to archive.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val dp = resources.displayMetrics.density
        val padding = (16 * dp).toInt()
        val halfPad = (8 * dp).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, halfPad, padding, halfPad)
        }

        val labelArchive = TextView(this).apply { text = "Archive name (current tour):" }
        val editArchiveName = EditText(this).apply {
            setText(dataManager.getCurrentTourName())
        }
        val labelNew = TextView(this).apply {
            text = "New tour name:"
            setPadding(0, halfPad, 0, 0)
        }
        val editNewTourName = EditText(this).apply {
            hint = "e.g. 2027 Europe Tour"
        }

        container.addView(labelArchive)
        container.addView(editArchiveName)
        container.addView(labelNew)
        container.addView(editNewTourName)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Archive Tour & Start New")
            .setView(container)
            .setPositiveButton("Archive & Start New", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val archiveName = editArchiveName.text.toString().trim()
                val newTourName = editNewTourName.text.toString().trim()
                if (archiveName.isEmpty()) {
                    editArchiveName.error = "Please enter an archive name"
                    return@setOnClickListener
                }
                if (newTourName.isEmpty()) {
                    editNewTourName.error = "Please enter a name for the new tour"
                    return@setOnClickListener
                }
                if (dataManager.archiveTour(archiveName)) {
                    dataManager.setCurrentTourName(newTourName)
                    supportActionBar?.title = newTourName
                    loadGigsData()
                    updateSpinner()
                    venueSpinner.setSelection(0)
                    selectedGigIndex = -1
                    detailsContainer.visibility = View.GONE
                    actionButtonsContainer.visibility = View.GONE
                    dialog.dismiss()
                    Toast.makeText(this, "Tour archived: $archiveName", Toast.LENGTH_LONG).show()
                } else {
                    dialog.dismiss()
                    Toast.makeText(this, "Error archiving tour", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Gig")
            .setMessage("Are you sure you want to delete ${gigs[selectedGigIndex].cityVenue}?")
            .setPositiveButton("Delete") { _, _ ->
                if (dataManager.deleteGig(selectedGigIndex)) {
                    loadGigsData()
                    updateSpinner()
                    venueSpinner.setSelection(0)
                    Toast.makeText(this, "Gig deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Error deleting gig", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun displayGigDetails(gig: Gig) {
        detailsContainer.visibility = View.VISIBLE

        checkComplete.isChecked = gig.isComplete ?: false
        dateText.text = formatDisplayDate(gig.date)
        cityVenueText.text = (gig.cityVenue ?: "").ifEmpty { "N/A" }
        
        highlightStatus(ticketsWithMeText, gig.ticketsWithMe ?: "")
        highlightInText(whereTicketsText, gig.whereTicketsAre ?: "")
        accommodationText.text = (gig.accommodation ?: "").ifEmpty { "N/A" }
        whereAccomBoughtText.text = (gig.whereAccomBought ?: "").ifEmpty { "N/A" }
        accomDatesText.text = (gig.accomDates ?: "").ifEmpty { "N/A" }
        highlightInText(costText, gig.cost ?: "")
        highlightStatus(paidText, gig.paid ?: "")
        accomCommentsText.text = (gig.accomComments ?: "").ifEmpty { "N/A" }
        highlightInText(travelDetailsText, gig.travelDetails ?: "")
    }

    private fun highlightStatus(textView: TextView, value: String?) {
        val input = value ?: ""
        textView.text = input.ifEmpty { "N/A" }
        when {
            input.equals("Yes", ignoreCase = true) -> {
                textView.setBackgroundColor(Color.parseColor("#4CAF50")) // Green background
                textView.setTextColor(Color.WHITE)
                textView.setPadding(16, 8, 16, 8)
            }
            input.equals("No", ignoreCase = true) -> {
                textView.setBackgroundColor(Color.parseColor("#F44336")) // Red background
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

    private fun highlightInText(textView: TextView, text: String?) {
        val input = text ?: ""
        if (input.isEmpty()) {
            textView.text = "N/A"
            return
        }

        val builder = SpannableStringBuilder(input)
        
        // Match "Yes" (case insensitive) as a whole word
        val yesPattern = Pattern.compile("\\bYes\\b", Pattern.CASE_INSENSITIVE)
        val yesMatcher = yesPattern.matcher(input)
        while (yesMatcher.find()) {
            builder.setSpan(BackgroundColorSpan(Color.parseColor("#4CAF50")), yesMatcher.start(), yesMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(ForegroundColorSpan(Color.WHITE), yesMatcher.start(), yesMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Match "No" (case insensitive) as a whole word
        val noPattern = Pattern.compile("\\bNo\\b", Pattern.CASE_INSENSITIVE)
        val noMatcher = noPattern.matcher(input)
        while (noMatcher.find()) {
            builder.setSpan(BackgroundColorSpan(Color.parseColor("#F44336")), noMatcher.start(), noMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            builder.setSpan(ForegroundColorSpan(Color.WHITE), noMatcher.start(), noMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        textView.text = builder
    }
}
