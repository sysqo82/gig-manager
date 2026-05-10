package com.suede.gigmanager

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
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.graphics.toColorInt

class ArchiveViewActivity : AppCompatActivity() {

    private lateinit var archiveGigSpinner: Spinner
    private lateinit var archiveDetailsContainer: LinearLayout
    private lateinit var archiveCompleteBadge: TextView
    private lateinit var archiveGigDateText: TextView
    private lateinit var archiveCityVenueText: TextView
    private lateinit var archiveTicketsWithMeText: TextView
    private lateinit var archiveWhereTicketsText: TextView
    private lateinit var archiveAccommodationText: TextView
    private lateinit var archiveWhereAccomBoughtText: TextView
    private lateinit var archiveAccomDatesText: TextView
    private lateinit var archiveCostText: TextView
    private lateinit var archivePaidText: TextView
    private lateinit var archiveAccomCommentsText: TextView
    private lateinit var archiveTravelDetailsText: TextView

    private lateinit var archive: TourArchive

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
        setContentView(R.layout.activity_archive_view)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val json = intent.getStringExtra(EXTRA_ARCHIVE_JSON) ?: run { finish(); return }
        archive = Gson().fromJson(json, TourArchive::class.java) ?: run { finish(); return }

        supportActionBar?.title = archive.tourName
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
            if (dates.isEmpty()) null
            else if (dates.min() == dates.max()) short.format(dates.min())
            else "${short.format(dates.min())} – ${short.format(dates.max())}"
        }
        val gigCount = archive.gigs.size
        supportActionBar?.subtitle = buildString {
            if (dateRange != null) append("$dateRange · ")
            append("$gigCount gig${if (gigCount != 1) "s" else ""}")
        }

        archiveGigSpinner = findViewById(R.id.archiveGigSpinner)
        archiveDetailsContainer = findViewById(R.id.archiveDetailsContainer)
        archiveCompleteBadge = findViewById(R.id.archiveCompleteBadge)
        archiveGigDateText = findViewById(R.id.archiveGigDateText)
        archiveCityVenueText = findViewById(R.id.archiveCityVenueText)
        archiveTicketsWithMeText = findViewById(R.id.archiveTicketsWithMeText)
        archiveWhereTicketsText = findViewById(R.id.archiveWhereTicketsText)
        archiveAccommodationText = findViewById(R.id.archiveAccommodationText)
        archiveWhereAccomBoughtText = findViewById(R.id.archiveWhereAccomBoughtText)
        archiveAccomDatesText = findViewById(R.id.archiveAccomDatesText)
        archiveCostText = findViewById(R.id.archiveCostText)
        archivePaidText = findViewById(R.id.archivePaidText)
        archiveAccomCommentsText = findViewById(R.id.archiveAccomCommentsText)
        archiveTravelDetailsText = findViewById(R.id.archiveTravelDetailsText)

        setupSpinner()
    }

    private fun setupSpinner() {
        val items = mutableListOf("Select a gig...")
        items.addAll(archive.gigs.map { gig ->
            val city = gig.cityVenue?.split(" ")?.firstOrNull() ?: gig.cityVenue ?: "Unknown"
            "${formatDisplayDate(gig.date)} – $city"
        })

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                if (position > 0 && archive.gigs[position - 1].isComplete == true) {
                    view.setBackgroundColor("#C8E6C9".toColorInt())
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT)
                }
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        archiveGigSpinner.adapter = adapter

        archiveGigSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    archiveDetailsContainer.visibility = View.GONE
                } else {
                    displayGigDetails(archive.gigs[position - 1])
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                archiveDetailsContainer.visibility = View.GONE
            }
        }
    }

    private fun displayGigDetails(gig: Gig) {
        archiveDetailsContainer.visibility = View.VISIBLE

        archiveCompleteBadge.visibility = if (gig.isComplete == true) View.VISIBLE else View.GONE
        archiveGigDateText.text = formatDisplayDate(gig.date)
        archiveCityVenueText.text = (gig.cityVenue ?: "").ifEmpty { "N/A" }
        archiveCityVenueText.paintFlags = archiveCityVenueText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        archiveCityVenueText.setOnClickListener {
            val venue = archiveCityVenueText.text.toString()
            if (venue.isNotEmpty() && venue != "N/A") openGoogleMaps(venue)
        }
        highlightStatus(archiveTicketsWithMeText, gig.ticketsWithMe ?: "")
        highlightInText(archiveWhereTicketsText, gig.whereTicketsAre ?: "")
        archiveAccommodationText.text = (gig.accommodation ?: "").ifEmpty { "N/A" }
        archiveAccommodationText.paintFlags = archiveAccommodationText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        archiveAccommodationText.setOnClickListener {
            val location = archiveAccommodationText.text.toString()
            if (location.isNotEmpty() && location != "N/A") openGoogleMaps(location)
        }
        archiveWhereAccomBoughtText.text = (gig.whereAccomBought ?: "").ifEmpty { "N/A" }
        archiveAccomDatesText.text = (gig.accomDates ?: "").ifEmpty { "N/A" }
        highlightInText(archiveCostText, gig.cost ?: "")
        highlightStatus(archivePaidText, gig.paid ?: "")
        archiveAccomCommentsText.text = (gig.accomComments ?: "").ifEmpty { "N/A" }
        highlightInText(archiveTravelDetailsText, gig.travelDetails ?: "")
    }

    private fun openGoogleMaps(address: String) {
        val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")
        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(address)}"))
            startActivity(webIntent)
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
                try { date = format.parse(input); if (date != null) break } catch (e: Exception) { /* try next */ }
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

        fun start(context: android.content.Context, archive: TourArchive) {
            val intent = Intent(context, ArchiveViewActivity::class.java)
            intent.putExtra(EXTRA_ARCHIVE_JSON, Gson().toJson(archive))
            context.startActivity(intent)
        }
    }
}
