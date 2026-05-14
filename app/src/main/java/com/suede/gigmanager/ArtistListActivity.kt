package com.suede.gigmanager

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.suede.gigmanager.databinding.ActivityArtistListBinding

class ArtistListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityArtistListBinding
    private lateinit var syncService: GigSyncService
    private val artists = mutableListOf<Artist>()
    private lateinit var adapter: ArtistAdapter
    private var hasOrphanedTours = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityArtistListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // White icons on dark status bar (works on API 35+ edge-to-edge)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Artists"

        syncService = GigSyncService(this)

        adapter = ArtistAdapter(artists,
            onClick = { artist -> TourListActivity.start(this, artist) },
            onLongClick = { artist -> showArtistOptions(artist) }
        )
        binding.recyclerArtists.layoutManager = LinearLayoutManager(this)
        binding.recyclerArtists.adapter = adapter

        binding.fabAddArtist.setOnClickListener { showAddArtistDialog() }

        loadArtists()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_account, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_sync_account) {
            AccountActivity.start(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadArtists() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerArtists.visibility = View.GONE
        binding.emptyState.visibility = View.GONE

        Thread {
            val result = syncService.fetchArtists()
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                when (result) {
                    is ApiResult.Success -> {
                        hasOrphanedTours = result.data.hasOrphanedTours
                        artists.clear()
                        artists.addAll(result.data.artists)
                        adapter.notifyDataSetChanged()
                        if (artists.isEmpty()) {
                            binding.emptyState.visibility = View.VISIBLE
                        } else {
                            binding.recyclerArtists.visibility = View.VISIBLE
                        }
                    }
                    is ApiResult.Error -> {
                        binding.recyclerArtists.visibility = View.VISIBLE
                        Toast.makeText(this, "Could not load artists: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun showAddArtistDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_artist, null)
        val etName = dialogView.findViewById<EditText>(R.id.etArtistName)
        val progressSearch = dialogView.findViewById<ProgressBar>(R.id.progressDeezerSearch)
        val rvDeezer = dialogView.findViewById<RecyclerView>(R.id.rvDeezerResults)
        val tvNoResults = dialogView.findViewById<TextView>(R.id.tvNoDeezerResults)

        var selectedImageUrl: String? = null
        var suppressWatcher = false

        val deezerAdapter = DeezerResultAdapter()
        rvDeezer.layoutManager = LinearLayoutManager(this)
        rvDeezer.adapter = deezerAdapter

        val watcher = debouncedDeezerSearch(etName, progressSearch, rvDeezer, tvNoResults, deezerAdapter,
            isSuppressed = { suppressWatcher },
            onQueryChanged = { selectedImageUrl = null }
        )
        etName.addTextChangedListener(watcher)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Artist")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) createArtist(name, selectedImageUrl)
            }
            .setNegativeButton("Cancel", null)
            .create()

        deezerAdapter.onSelect = { deezerArtist ->
            suppressWatcher = true
            etName.setText(deezerArtist.name)
            suppressWatcher = false
            selectedImageUrl = deezerArtist.imageUrl
            deezerAdapter.collapseToSelected()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(dialog.window?.decorView?.windowToken, 0)
        }

        dialog.show()
    }

    private fun createArtist(name: String, imageUrl: String?) {
        Thread {
            val result = syncService.createArtist(name, imageUrl)
            runOnUiThread {
                when (result) {
                    is ApiResult.Success -> {
                        val newArtist = result.data
                        artists.add(newArtist)
                        adapter.notifyItemInserted(artists.size - 1)
                        binding.emptyState.visibility = View.GONE
                        binding.recyclerArtists.visibility = View.VISIBLE
                        if (hasOrphanedTours) {
                            promptAssignOrphanedTours(newArtist)
                        }
                    }
                    is ApiResult.Error -> {
                        Toast.makeText(this, "Failed to create artist: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun promptAssignOrphanedTours(artist: Artist) {
        AlertDialog.Builder(this)
            .setTitle("Assign existing data?")
            .setMessage("Assign your existing tour data to ${artist.name}?")
            .setPositiveButton("Yes") { _, _ ->
                hasOrphanedTours = false  // clear on main thread before background work starts
                Thread {
                    syncService.assignOrphanedTours(artist.id)
                }.start()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showArtistOptions(artist: Artist) {
        val options = arrayOf("Update image", "Delete")
        AlertDialog.Builder(this)
            .setTitle(artist.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showUpdateImageDialog(artist)
                    1 -> confirmDeleteArtist(artist)
                }
            }
            .show()
    }

    private fun confirmDeleteArtist(artist: Artist) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${artist.name}?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                Thread {
                    val result = syncService.deleteArtist(artist.id)
                    runOnUiThread {
                        when (result) {
                            is ApiResult.Success -> {
                                val idx = artists.indexOfFirst { it.id == artist.id }
                                if (idx >= 0) {
                                    artists.removeAt(idx)
                                    adapter.notifyItemRemoved(idx)
                                }
                                if (artists.isEmpty()) {
                                    binding.recyclerArtists.visibility = View.GONE
                                    binding.emptyState.visibility = View.VISIBLE
                                }
                            }
                            is ApiResult.Error -> Toast.makeText(
                                this,
                                if (result.message.contains("gigs", ignoreCase = true))
                                    "Can't delete — ${artist.name} has gigs"
                                else "Delete failed: ${result.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showUpdateImageDialog(artist: Artist) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_artist, null)
        val etName = dialogView.findViewById<EditText>(R.id.etArtistName)
        val progressSearch = dialogView.findViewById<ProgressBar>(R.id.progressDeezerSearch)
        val rvDeezer = dialogView.findViewById<RecyclerView>(R.id.rvDeezerResults)
        val tvNoResults = dialogView.findViewById<TextView>(R.id.tvNoDeezerResults)

        var selectedImageUrl: String? = artist.imageUrl
        var suppressWatcher = false

        val deezerAdapter = DeezerResultAdapter()
        rvDeezer.layoutManager = LinearLayoutManager(this)
        rvDeezer.adapter = deezerAdapter

        val watcher = debouncedDeezerSearch(etName, progressSearch, rvDeezer, tvNoResults, deezerAdapter,
            isSuppressed = { suppressWatcher },
            onQueryChanged = { selectedImageUrl = null }
        )

        val dialog = AlertDialog.Builder(this)
            .setTitle("Update Image")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                if (selectedImageUrl != artist.imageUrl) saveArtistImage(artist, selectedImageUrl)
            }
            .setNegativeButton("Cancel", null)
            .create()

        deezerAdapter.onSelect = { deezerArtist ->
            suppressWatcher = true
            etName.setText(deezerArtist.name)
            suppressWatcher = false
            selectedImageUrl = deezerArtist.imageUrl
            deezerAdapter.collapseToSelected()
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(dialog.window?.decorView?.windowToken, 0)
        }

        // Pre-populate and trigger initial search
        progressSearch.visibility = View.VISIBLE
        Thread {
            val result = syncService.searchDeezer(artist.name)
            runOnUiThread {
                progressSearch.visibility = View.GONE
                if (result is ApiResult.Success && result.data.isNotEmpty()) {
                    rvDeezer.visibility = View.VISIBLE
                    deezerAdapter.updateResults(result.data)
                }
                suppressWatcher = true
                etName.setText(artist.name)
                etName.setSelection(etName.text.length)
                suppressWatcher = false
                etName.addTextChangedListener(watcher)
            }
        }.start()

        dialog.show()
    }

    private fun debouncedDeezerSearch(
        etName: EditText,
        progressSearch: ProgressBar,
        rvDeezer: RecyclerView,
        tvNoResults: TextView,
        adapter: DeezerResultAdapter,
        isSuppressed: () -> Boolean = { false },
        onQueryChanged: () -> Unit
    ): TextWatcher {
        val handler = Handler(Looper.getMainLooper())
        var searchRunnable: Runnable? = null
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isSuppressed()) return
                val query = s?.toString()?.trim() ?: return
                onQueryChanged()
                searchRunnable?.let { handler.removeCallbacks(it) }
                if (query.length < 2) {
                    rvDeezer.visibility = View.GONE
                    tvNoResults.visibility = View.GONE
                    progressSearch.visibility = View.GONE
                    return
                }
                progressSearch.visibility = View.VISIBLE
                rvDeezer.visibility = View.GONE
                tvNoResults.visibility = View.GONE
                searchRunnable = Runnable {
                    Thread {
                        val result = syncService.searchDeezer(query)
                        runOnUiThread {
                            progressSearch.visibility = View.GONE
                            when (result) {
                                is ApiResult.Success -> {
                                    if (result.data.isEmpty()) {
                                        tvNoResults.visibility = View.VISIBLE
                                    } else {
                                        rvDeezer.visibility = View.VISIBLE
                                        adapter.updateResults(result.data)
                                    }
                                }
                                is ApiResult.Error -> {
                                    tvNoResults.visibility = View.VISIBLE
                                    tvNoResults.text = "Search failed"
                                }
                            }
                        }
                    }.start()
                }.also { handler.postDelayed(it, 500) }
            }
        }
    }

    private fun saveArtistImage(artist: Artist, imageUrl: String?) {
        Thread {
            val result = syncService.updateArtistImage(artist.id, imageUrl)
            runOnUiThread {
                when (result) {
                    is ApiResult.Success -> {
                        val idx = artists.indexOfFirst { it.id == artist.id }
                        if (idx >= 0) {
                            artists[idx] = artist.copy(imageUrl = imageUrl)
                            adapter.notifyItemChanged(idx)
                        }
                    }
                    is ApiResult.Error -> Toast.makeText(this, "Failed to update image", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ---- Adapters -----------------------------------------------------------

    private class ArtistAdapter(
        private val items: List<Artist>,
        private val onClick: (Artist) -> Unit,
        private val onLongClick: (Artist) -> Unit
    ) : RecyclerView.Adapter<ArtistAdapter.VH>() {

        inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.imgArtist)
            val name: TextView = view.findViewById(R.id.tvArtistName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_artist, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val artist = items[position]
            holder.name.text = artist.name
            if (artist.imageUrl != null) {
                Glide.with(holder.img)
                    .load(artist.imageUrl)
                    .transform(CircleCrop())
                    .placeholder(R.drawable.circle_image_bg)
                    .into(holder.img)
            } else {
                holder.img.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            holder.view.setOnClickListener { onClick(artist) }
            holder.view.setOnLongClickListener { onLongClick(artist); true }
        }
    }

    inner class DeezerResultAdapter : RecyclerView.Adapter<DeezerResultAdapter.VH>() {

        private var items: List<DeezerArtist> = emptyList()
        private var selectedPosition = -1
        var onSelect: ((DeezerArtist) -> Unit)? = null

        fun updateResults(newItems: List<DeezerArtist>) {
            items = newItems
            selectedPosition = -1
            notifyDataSetChanged()
        }

        fun collapseToSelected() {
            val pos = selectedPosition
            if (pos < 0 || pos >= items.size) return
            val selected = items[pos]
            items = listOf(selected)
            selectedPosition = 0
            notifyDataSetChanged()
        }

        inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.imgDeezerArtist)
            val name: TextView = view.findViewById(R.id.tvDeezerArtistName)
            val check: ImageView = view.findViewById(R.id.imgDeezerCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_deezer_artist, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val deezerArtist = items[position]
            val selected = position == selectedPosition
            holder.name.text = deezerArtist.name
            holder.name.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            holder.check.visibility = if (selected) View.VISIBLE else View.GONE
            holder.view.setBackgroundColor(
                if (selected) 0x1A6200EA else android.graphics.Color.TRANSPARENT
            )
            if (deezerArtist.imageUrl != null) {
                Glide.with(holder.img)
                    .load(deezerArtist.imageUrl)
                    .transform(CircleCrop())
                    .placeholder(R.drawable.circle_image_bg)
                    .into(holder.img)
            } else {
                holder.img.setImageResource(android.R.drawable.ic_menu_gallery)
            }
            holder.view.setOnClickListener {
                val p = holder.adapterPosition
                if (p < 0) return@setOnClickListener
                val prev = selectedPosition
                selectedPosition = p
                if (prev >= 0 && prev != p) notifyItemChanged(prev)
                notifyItemChanged(p)
                onSelect?.invoke(deezerArtist)
            }
        }
    }
}
