package com.suede.gigmanager

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerGigs: RecyclerView
    private lateinit var emptyGigsText: TextView
    private lateinit var btnAddGig: Button
    private lateinit var btnArchiveTour: Button
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var archiveDrawerList: ListView
    private lateinit var archivesEmptyText: TextView

    private lateinit var dataManager: GigDataManager
    private lateinit var syncService: GigSyncService
    private var gigs: MutableList<Gig> = mutableListOf()

    private val artistId get() = intent.getIntExtra("extra_artist_id", -1).takeIf { it > 0 }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(this, R.color.purple_700)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        setContentView(R.layout.activity_main)

        val artistIdVal = intent.getIntExtra("extra_artist_id", -1).takeIf { it > 0 }
        dataManager = GigDataManager(this, artistIdVal)
        syncService = GigSyncService(this)

        if (!syncService.isLoggedIn()) {
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        recyclerGigs = findViewById(R.id.recyclerGigs)
        emptyGigsText = findViewById(R.id.emptyGigsText)
        btnAddGig = findViewById(R.id.btnAddGig)
        btnArchiveTour = findViewById(R.id.btnArchiveTour)
        drawerLayout = findViewById(R.id.drawerLayout)
        archiveDrawerList = findViewById(R.id.archiveDrawerList)
        archivesEmptyText = findViewById(R.id.archivesEmptyText)

        recyclerGigs.layoutManager = LinearLayoutManager(this)

        loadGigsData()
        supportActionBar?.title = dataManager.getCurrentTourName()
        refreshPillList()

        btnAddGig.setOnClickListener { showAddGigDialog() }
        btnArchiveTour.setOnClickListener { showArchiveTourDialog() }
    }

    override fun onResume() {
        super.onResume()
        loadGigsData()
        supportActionBar?.title = dataManager.getCurrentTourName()
        invalidateOptionsMenu()
        refreshPillList()
    }

    private fun loadGigsData() {
        gigs = dataManager.loadGigs().toMutableList()
    }

    private fun refreshPillList() {
        if (gigs.isEmpty()) {
            recyclerGigs.visibility = View.GONE
            emptyGigsText.visibility = View.VISIBLE
        } else {
            recyclerGigs.visibility = View.VISIBLE
            emptyGigsText.visibility = View.GONE
            recyclerGigs.adapter = GigAdapter(gigs)
        }
    }

    private inner class GigAdapter(private val items: List<Gig>) :
        RecyclerView.Adapter<GigAdapter.VH>() {

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
            val gig = items[position]
            holder.dateText.text = formatDisplayDate(gig.date)
            holder.cityText.text = gig.cityVenue ?: "Unknown"
            val bgColor = if (gig.isComplete == true) Color.parseColor("#C8E6C9") else Color.WHITE
            holder.card.setCardBackgroundColor(bgColor)
            holder.card.setOnClickListener {
                val tours = dataManager.loadTours()
                val firstTour = tours.firstOrNull() ?: return@setOnClickListener
                // position is an index into active (non-archived) gigs; map back to full gigs index
                val activeIndices = firstTour.gigs.mapIndexedNotNull { i, g ->
                    if (g.isArchived != true) i else null
                }
                val fullIndex = activeIndices.getOrNull(position) ?: position
                GigDetailActivity.start(this@MainActivity, firstTour.id, fullIndex, artistId ?: 0)
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
            for (format in inputFormats) {
                try { date = format.parse(input); if (date != null) break } catch (e: Exception) { }
            }
            if (date != null) SimpleDateFormat("EEEE, d/M/yyyy", Locale.ENGLISH).format(date) else input
        } catch (e: Exception) { input }
    }

    @SuppressLint("DefaultLocale")
    private fun showAddGigDialog() {
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
            DatePickerDialog(this, { _, y, m, d ->
                editDate.setText(String.format("%d/%d/%d", d, m + 1, y))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        editAccomDates.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y1, m1, d1 ->
                val checkIn = String.format("%d/%d/%d", d1, m1 + 1, y1)
                DatePickerDialog(this, { _, y2, m2, d2 ->
                    editAccomDates.setText("$checkIn \u2013 ${String.format("%d/%d/%d", d2, m2 + 1, y2)}")
                }, y1, m1, d1).also { it.setTitle("Check-out date") }.show()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                .also { it.setTitle("Check-in date") }.show()
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
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
                isComplete = false
            )
            if (newGig.cityVenue.isNullOrEmpty()) {
                Toast.makeText(this, "City & Venue is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (dataManager.addGig(newGig)) {
                loadGigsData()
                refreshPillList()
                Toast.makeText(this, "Gig added", Toast.LENGTH_SHORT).show()
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
        val editArchiveName = EditText(this).apply { setText(dataManager.getCurrentTourName()) }
        val labelNew = TextView(this).apply {
            text = "New tour name:"
            setPadding(0, halfPad, 0, 0)
        }
        val editNewTourName = EditText(this).apply { hint = "e.g. 2027 Europe Tour" }

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
                if (archiveName.isEmpty()) { editArchiveName.error = "Please enter an archive name"; return@setOnClickListener }
                if (newTourName.isEmpty()) { editNewTourName.error = "Please enter a name for the new tour"; return@setOnClickListener }
                if (dataManager.archiveTour(archiveName)) {
                    dataManager.setCurrentTourName(newTourName)
                    supportActionBar?.title = newTourName
                    loadGigsData()
                    refreshPillList()
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
        if (item.itemId == R.id.action_archives) { openArchivesDrawer(); return true }
        if (item.itemId == R.id.action_sync_account) { AccountActivity.start(this); return true }
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
                    else "${short.format(dates.min())} \u2013 ${short.format(dates.max())}"
                }
                "${archive.tourName}\n$dateRange \u00b7 ${archive.gigs.size} gig${if (archive.gigs.size != 1) "s" else ""}"
            }
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_2, android.R.id.text1, items)
            archiveDrawerList.adapter = adapter
            archiveDrawerList.setOnItemClickListener { _, _, position, _ ->
                drawerLayout.closeDrawer(GravityCompat.END)
                ArchiveViewActivity.start(this, archives[position], artistId)
            }
        }
        drawerLayout.openDrawer(GravityCompat.END)
    }
}
