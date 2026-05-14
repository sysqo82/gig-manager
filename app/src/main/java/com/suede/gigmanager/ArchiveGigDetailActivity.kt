package com.suede.gigmanager

import android.content.Context
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
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class ArchiveGigDetailActivity : AppCompatActivity() {

    private lateinit var archive: TourArchive
    private var gigIndex: Int = 0
    private var artistId: Int? = null
    private val dataManager by lazy { GigDataManager(this, artistId) }

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
    private lateinit var btnDelete: Button

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
        setContentView(R.layout.activity_archive_gig_detail)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        artistId = intent.getIntExtra(EXTRA_ARTIST_ID, -1).takeIf { it > 0 }
        val archiveJson = intent.getStringExtra(EXTRA_ARCHIVE_JSON) ?: run { finish(); return }
        archive = Gson().fromJson(archiveJson, TourArchive::class.java) ?: run { finish(); return }
        gigIndex = intent.getIntExtra(EXTRA_GIG_INDEX, 0)

        completeBadge = findViewById(R.id.archiveCompleteBadge)
        gigDateText = findViewById(R.id.archiveGigDateText)
        cityVenueText = findViewById(R.id.archiveCityVenueText)
        ticketsWithMeText = findViewById(R.id.archiveTicketsWithMeText)
        whereTicketsText = findViewById(R.id.archiveWhereTicketsText)
        accommodationText = findViewById(R.id.archiveAccommodationText)
        whereAccomBoughtText = findViewById(R.id.archiveWhereAccomBoughtText)
        accomDatesText = findViewById(R.id.archiveAccomDatesText)
        costText = findViewById(R.id.archiveCostText)
        paidText = findViewById(R.id.archivePaidText)
        accomCommentsText = findViewById(R.id.archiveAccomCommentsText)
        travelDetailsText = findViewById(R.id.archiveTravelDetailsText)
        btnDelete = findViewById(R.id.btnDeleteArchiveGig)

        btnDelete.setOnClickListener { showDeleteConfirmation() }

        displayGig()
    }

    private fun displayGig() {
        val gig = archive.gigs[gigIndex]

        supportActionBar?.title = (gig.cityVenue ?: "Gig Details")
        supportActionBar?.subtitle = formatDisplayDate(gig.date)

        completeBadge.visibility = if (gig.isComplete == true) View.VISIBLE else View.GONE
        gigDateText.text = formatDisplayDate(gig.date)

        cityVenueText.text = (gig.cityVenue ?: "").ifEmpty { "N/A" }
        cityVenueText.paintFlags = cityVenueText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        cityVenueText.setOnClickListener {
            val venue = cityVenueText.text.toString()
            if (venue.isNotEmpty() && venue != "N/A") openGoogleMaps(venue)
        }

        highlightStatus(ticketsWithMeText, gig.ticketsWithMe ?: "")
        highlightInText(whereTicketsText, gig.whereTicketsAre ?: "")

        accommodationText.text = (gig.accommodation ?: "").ifEmpty { "N/A" }
        accommodationText.paintFlags = accommodationText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        accommodationText.setOnClickListener {
            val location = accommodationText.text.toString()
            if (location.isNotEmpty() && location != "N/A") openGoogleMaps(location)
        }

        whereAccomBoughtText.text = (gig.whereAccomBought ?: "").ifEmpty { "N/A" }
        accomDatesText.text = (gig.accomDates ?: "").ifEmpty { "N/A" }
        highlightInText(costText, gig.cost ?: "")
        highlightStatus(paidText, gig.paid ?: "")
        accomCommentsText.text = (gig.accomComments ?: "").ifEmpty { "N/A" }
        highlightInText(travelDetailsText, gig.travelDetails ?: "")
    }

    private fun showDeleteConfirmation() {
        val gig = archive.gigs[gigIndex]
        val label = "${formatDisplayDate(gig.date)} – ${gig.cityVenue ?: ""}"
        AlertDialog.Builder(this)
            .setTitle("Delete Gig")
            .setMessage("Remove \"$label\" from this archive?")
            .setPositiveButton("Delete") { _, _ -> performDelete() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDelete() {
        val updated = dataManager.deleteGigFromArchive(archive, gigIndex)
        if (updated == null) {
            Toast.makeText(this, "Failed to delete gig", Toast.LENGTH_SHORT).show()
            return
        }
        if (updated.gigs.isEmpty()) {
            dataManager.deleteArchive(updated)
            Toast.makeText(this, "Archive deleted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Gig deleted", Toast.LENGTH_SHORT).show()
        }
        finish()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_ARCHIVE_JSON = "extra_archive_json"
        const val EXTRA_GIG_INDEX = "extra_gig_index"
        const val EXTRA_ARTIST_ID = "extra_artist_id"

        fun start(context: Context, archive: TourArchive, gigIndex: Int, artistId: Int? = null) {
            val intent = Intent(context, ArchiveGigDetailActivity::class.java)
            intent.putExtra(EXTRA_ARCHIVE_JSON, Gson().toJson(archive))
            intent.putExtra(EXTRA_GIG_INDEX, gigIndex)
            if (artistId != null) intent.putExtra(EXTRA_ARTIST_ID, artistId)
            context.startActivity(intent)
        }
    }
}
