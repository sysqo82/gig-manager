package com.suede.gigmanager

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Locale

class ArchiveViewActivity : AppCompatActivity() {

    private lateinit var recyclerGigs: RecyclerView
    private lateinit var archive: TourArchive
    private var artistId: Int? = null
    private var justCreated = true
    private val dataManager by lazy { GigDataManager(this, artistId) }

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
        setContentView(R.layout.activity_archive_view)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        artistId = intent.getIntExtra(EXTRA_ARTIST_ID, -1).takeIf { it > 0 }
        val json = intent.getStringExtra(EXTRA_ARCHIVE_JSON) ?: run { finish(); return }
        archive = Gson().fromJson(json, TourArchive::class.java) ?: run { finish(); return }

        recyclerGigs = findViewById(R.id.recyclerArchiveGigs)
        recyclerGigs.layoutManager = LinearLayoutManager(this)

        setupPage()
    }

    override fun onResume() {
        super.onResume()
        // Skip the resume that immediately follows onCreate — archive is already set.
        if (justCreated) { justCreated = false; return }
        // Reload from disk so deletions done in ArchiveGigDetailActivity are reflected.
        val reloaded = dataManager.loadArchives().find {
            it.tourName == archive.tourName && it.archivedDate == archive.archivedDate
        }
        if (reloaded == null) {
            // Archive was fully deleted in the detail screen — close this screen too.
            finish()
            return
        }
        archive = reloaded
        setupPage()
    }

    private fun setupPage() {
        supportActionBar?.title = archive.tourName
        updateSubtitle()
        recyclerGigs.adapter = GigAdapter(archive.gigs)
    }

    private fun updateSubtitle() {
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
        val dateRange = when {
            dates.isEmpty() -> null
            dates.min() == dates.max() -> short.format(dates.min())
            else -> "${short.format(dates.min())} – ${short.format(dates.max())}"
        }
        val gigCount = archive.gigs.size
        supportActionBar?.subtitle = buildString {
            if (dateRange != null) append("$dateRange · ")
            append("$gigCount gig${if (gigCount != 1) "s" else ""}")
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    private inner class GigAdapter(private val gigs: List<Gig>) :
        RecyclerView.Adapter<GigAdapter.VH>() {

        inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
            val pillDate: TextView = view.findViewById(R.id.pillDate)
            val pillCity: TextView = view.findViewById(R.id.pillCity)
            val pillCard: MaterialCardView = view.findViewById(R.id.pillCard)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.item_archive_gig_pill, parent, false)
            return VH(v)
        }

        override fun getItemCount() = gigs.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val gig = gigs[position]
            holder.pillDate.text = formatDisplayDate(gig.date)
            holder.pillCity.text = (gig.cityVenue ?: "").ifEmpty { "Unknown" }
            holder.pillCard.setCardBackgroundColor(
                if (gig.isComplete == true) Color.parseColor("#C8E6C9")
                else Color.WHITE
            )
            holder.view.setOnClickListener {
                ArchiveGigDetailActivity.start(this@ArchiveViewActivity, archive, position, artistId)
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

    companion object {
        const val EXTRA_ARCHIVE_JSON = "extra_archive_json"
        const val EXTRA_ARTIST_ID = "extra_artist_id"

        fun start(context: android.content.Context, archive: TourArchive, artistId: Int? = null) {
            val intent = Intent(context, ArchiveViewActivity::class.java)
            intent.putExtra(EXTRA_ARCHIVE_JSON, Gson().toJson(archive))
            if (artistId != null) intent.putExtra(EXTRA_ARTIST_ID, artistId)
            context.startActivity(intent)
        }
    }
}
