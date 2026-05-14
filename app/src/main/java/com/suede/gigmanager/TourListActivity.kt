package com.suede.gigmanager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.suede.gigmanager.databinding.ActivityTourListBinding

class TourListActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ARTIST_ID = "extra_artist_id"
        private const val EXTRA_ARTIST_NAME = "extra_artist_name"
        private const val EXTRA_ARTIST_IMAGE_URL = "extra_artist_image_url"

        fun start(context: Context, artist: Artist) {
            context.startActivity(Intent(context, TourListActivity::class.java).apply {
                putExtra(EXTRA_ARTIST_ID, artist.id)
                putExtra(EXTRA_ARTIST_NAME, artist.name)
                putExtra(EXTRA_ARTIST_IMAGE_URL, artist.imageUrl)
            })
        }
    }

    private lateinit var binding: ActivityTourListBinding
    private var artistId = 0
    private var artistName = ""
    private lateinit var dataManager: GigDataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityTourListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        artistId = intent.getIntExtra(EXTRA_ARTIST_ID, 0)
        artistName = intent.getStringExtra(EXTRA_ARTIST_NAME) ?: ""
        dataManager = GigDataManager(this, artistId)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = artistName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        binding.fabNewTour.setOnClickListener { showNewTourDialog() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_tour_list, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_restore_from_server -> { showRestoreFromServerDialog(); true }
            R.id.action_sync_account        -> { AccountActivity.start(this); true }
            else                            -> super.onOptionsItemSelected(item)
        }
    }

    private fun showRestoreFromServerDialog() {
        val syncService = GigSyncService(this)
        if (!syncService.isLoggedIn()) {
            AccountActivity.start(this)
            return
        }
        val progress = androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage("Checking server…")
            .setCancelable(false)
            .create()
        progress.show()

        Thread {
            val result = syncService.pullForArtist(artistId)
            runOnUiThread {
                progress.dismiss()
                when (result) {
                    is ApiResult.Error -> androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Restore Failed")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .show()
                    is ApiResult.Success -> {
                        val data = result.data
                        val gigCount = data.gigs.size
                        val archiveCount = data.archives.size
                        if (gigCount == 0 && archiveCount == 0) {
                            androidx.appcompat.app.AlertDialog.Builder(this)
                                .setTitle("Nothing to Restore")
                                .setMessage("The server has no gig data for this artist yet.")
                                .setPositiveButton("OK", null)
                                .show()
                            return@runOnUiThread
                        }
                        androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Restore from Server")
                            .setMessage(
                                "Server copy:\n" +
                                "• Tour: ${data.tourName.ifEmpty { "(unnamed)" }}\n" +
                                "• $gigCount gig${if (gigCount != 1) "s" else ""}, $archiveCount archive${if (archiveCount != 1) "s" else ""}\n" +
                                (if (data.lastSyncedAt != null) "• Last synced: ${data.lastSyncedAt}\n" else "") +
                                "\nThis will replace your local data for $artistName."
                            )
                            .setPositiveButton("Restore") { _, _ ->
                                dataManager.savePreRestoreBackup()
                                dataManager.restoreFromServer(data.tourName, data.gigs, data.archives)
                                showTours()
                                Toast.makeText(this, "Data restored from server", Toast.LENGTH_LONG).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        showTours()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun showTours() {
        val tours = dataManager.loadTours()
        if (tours.isEmpty()) {
            binding.recyclerTours.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerTours.visibility = View.VISIBLE
            binding.recyclerTours.layoutManager = LinearLayoutManager(this)
            binding.recyclerTours.adapter = TourAdapter(tours)
        }
    }

    private fun showNewTourDialog() {
        val et = EditText(this).apply {
            hint = "e.g. 2027 Europe Tour"
            setSingleLine()
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        et.setPadding(padding, padding / 2, padding, padding / 2)
        AlertDialog.Builder(this)
            .setTitle("New Tour")
            .setView(et)
            .setPositiveButton("Create") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Tour name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val tour = dataManager.addTour(name)
                if (tour != null) {
                    showTours()
                    // Open the new tour immediately
                    TourDetailActivity.start(this, tour.id, artistId)
                } else {
                    Toast.makeText(this, "Could not create tour", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        et.requestFocus()
    }

    private fun showTourOptionsMenu(tour: Tour) {
        AlertDialog.Builder(this)
            .setTitle(tour.name)
            .setItems(arrayOf("Edit name", "Delete")) { _, which ->
                when (which) {
                    0 -> showRenameTourDialog(tour)
                    1 -> showDeleteTourDialog(tour)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameTourDialog(tour: Tour) {
        val et = EditText(this).apply {
            hint = "Tour name"
            setText(tour.name)
            setSingleLine()
            selectAll()
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        et.setPadding(padding, padding / 2, padding, padding / 2)
        AlertDialog.Builder(this)
            .setTitle("Rename Tour")
            .setView(et)
            .setPositiveButton("Save") { _, _ ->
                val name = et.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Tour name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (dataManager.renameTour(tour.id, name)) {
                    showTours()
                } else {
                    Toast.makeText(this, "Could not rename tour", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        et.requestFocus()
    }

    private fun showDeleteTourDialog(tour: Tour) {
        val activeCount = tour.gigs.count { it.isArchived != true }
        val totalCount = tour.gigs.size
        val msg = if (totalCount == 0)
            "\"${tour.name}\" is empty. Delete it?"
        else
            "Delete \"${tour.name}\"? It has $activeCount active and ${totalCount - activeCount} archived dates. This cannot be undone."
        AlertDialog.Builder(this)
            .setTitle("Delete Tour")
            .setMessage(msg)
            .setPositiveButton("Delete") { _, _ ->
                dataManager.deleteTour(tour.id)
                showTours()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- Adapter --------------------------------------------------------

    private inner class TourAdapter(private val items: List<Tour>) :
        RecyclerView.Adapter<TourAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView.findViewById(R.id.tourCard)
            val name: TextView = itemView.findViewById(R.id.tvTourName)
            val meta: TextView = itemView.findViewById(R.id.tvTourMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tour_card, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val tour = items[position]
            holder.name.text = tour.name
            val active = tour.gigs.count { it.isArchived != true }
            val archived = tour.gigs.count { it.isArchived == true }
            holder.meta.text = buildString {
                append("$active active")
                if (archived > 0) append("  \u00b7  $archived archived")
            }
            holder.card.setOnClickListener {
                TourDetailActivity.start(this@TourListActivity, tour.id, artistId)
            }
            holder.card.setOnLongClickListener {
                showTourOptionsMenu(tour)
                true
            }
        }
    }
}
