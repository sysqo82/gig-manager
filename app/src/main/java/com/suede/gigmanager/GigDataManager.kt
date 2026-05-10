package com.suede.gigmanager

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class TourArchive(
    val tourName: String,
    val archivedDate: String,
    val gigs: List<Gig>
)

class GigDataManager(private val context: Context) {
    
    private val fileName = "gigs_data_local.json"
    private val gson = Gson()
    private val prefs by lazy { context.getSharedPreferences("gig_manager_prefs", Context.MODE_PRIVATE) }
    private val syncService by lazy { GigSyncService(context) }

    fun getCurrentTourName(): String = prefs.getString("current_tour_name", "New Tour") ?: "New Tour"

    fun setCurrentTourName(name: String) {
        prefs.edit().putString("current_tour_name", name).apply()
    }

    fun loadArchives(): List<TourArchive> {
        return try {
            (context.filesDir.listFiles { file ->
                file.name.startsWith("archive_") && file.name.endsWith(".json")
            } ?: emptyArray()).mapNotNull { file ->
                try {
                    gson.fromJson(file.readText(), TourArchive::class.java)
                } catch (e: Exception) {
                    null
                }
            }.sortedByDescending { it.archivedDate }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun loadGigs(): List<Gig> {
        // First check if we have a saved version in internal storage
        val file = File(context.filesDir, fileName)
        
        return if (file.exists()) {
            // Load from internal storage (user's modifications)
            try {
                val json = file.readText()
                val type = object : TypeToken<List<Gig>>() {}.type
                val gigs: List<Gig> = gson.fromJson(json, type)
                val sorted = gigs.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
                if (gigs != sorted) {
                    // persist the sorted order so future loads reflect it
                    saveGigs(sorted)
                }
                return sorted
            } catch (e: Exception) {
                e.printStackTrace()
                loadDefaultGigs()
            }
        } else {
            // Load from raw resources (initial data)
            val gigs = loadDefaultGigs()
            // Save it to internal storage for future modifications (sorted)
            val sorted = gigs.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
            saveGigs(sorted)
            sorted
        }
    }

    private fun loadDefaultGigs(): List<Gig> {
        return try {
            val inputStream = context.resources.openRawResource(R.raw.gigs_data)
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<Gig>>() {}.type
            val gigs: List<Gig> = gson.fromJson(reader, type)
            reader.close()
            gigs.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveGigs(gigs: List<Gig>): Boolean {
        return try {
            val file = File(context.filesDir, fileName)
            val json = gson.toJson(gigs)
            file.writeText(json)
            backgroundSync(gigs)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Fire-and-forget push to server. Silently ignored if not logged in or offline. */
    private fun backgroundSync(gigs: List<Gig>) {
        if (!syncService.isLoggedIn()) return
        Thread {
            try {
                val result = syncService.push(getCurrentTourName(), gigs, loadArchives())
                when (result) {
                    is ApiResult.Success -> Log.d("GigSync", "Auto-sync OK: ${result.data}")
                    is ApiResult.Error -> Log.e("GigSync", "Auto-sync failed: ${result.message}")
                }
            } catch (e: Exception) {
                Log.e("GigSync", "Auto-sync exception", e)
            }
        }.start()
    }

    fun addGig(gig: Gig): Boolean {
        val gigs = loadGigs().toMutableList()
        // Insert in sorted order by parsed date
        val insertIndex = gigs.indexOfFirst { existing ->
            val ed = parseGigDate(existing)
            val nd = parseGigDate(gig)
            when {
                ed == null && nd == null -> false
                ed == null -> true
                nd == null -> false
                else -> nd.isBefore(ed)
            }
        }
        if (insertIndex == -1) gigs.add(gig) else gigs.add(insertIndex, gig)
        return saveGigs(gigs)
    }

    fun updateGig(index: Int, gig: Gig): Boolean {
        val gigs = loadGigs().toMutableList()
        if (index in gigs.indices) {
            gigs[index] = gig
            // Re-sort after update to maintain order
            val sorted = gigs.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
            return saveGigs(sorted)
        }
        return false
    }

    private fun parseGigDate(gig: Gig): LocalDate? {
        val raw = gig.date ?: return null
        val trimmed = raw.trim()
        // Try formats: with weekday, without, and numeric slash formats (e.g. 7/2/2026)
        val formats = listOf(
            "EEEE, d MMMM uuuu",
            "d MMMM uuuu",
            "EEEE, d/M/uuuu",
            "d/M/uuuu",
            "EEEE, d/M/yyyy",
            "d/M/yyyy"
        )
        for (fmt in formats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH)
                return LocalDate.parse(trimmed, formatter)
            } catch (e: DateTimeParseException) {
                // try next
            }
        }
        return null
    }

    fun deleteGig(index: Int): Boolean {
        val gigs = loadGigs().toMutableList()
        if (index in gigs.indices) {
            gigs.removeAt(index)
            return saveGigs(gigs)
        }
        return false
    }

    // ---- Restore helpers -------------------------------------------------

    /**
     * Save a timestamped snapshot of the current local data before overwriting it.
     * Files are named pre_restore_TIMESTAMP.json and stored in internal storage.
     */
    fun savePreRestoreBackup() {
        try {
            val gigs = loadGigs()
            val archives = loadArchives()
            val tourName = getCurrentTourName()
            val snapshot = mapOf("tourName" to tourName, "gigs" to gigs, "archives" to archives)
            val timestamp = System.currentTimeMillis()
            val file = File(context.filesDir, "pre_restore_$timestamp.json")
            file.writeText(gson.toJson(snapshot))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Write server archives back to local archive files.
     * Clears all existing archive_*.json files first to avoid stale data.
     */
    fun restoreArchives(archives: List<TourArchive>) {
        // Remove existing archives
        context.filesDir.listFiles { f -> f.name.startsWith("archive_") && f.name.endsWith(".json") }
            ?.forEach { it.delete() }
        // Write restored archives
        archives.forEach { archive ->
            try {
                val sanitized = archive.tourName
                    .replace(Regex("[^a-zA-Z0-9_\\- ]"), "").trim()
                    .replace(" ", "_").take(50)
                val timestamp = System.currentTimeMillis()
                val file = File(context.filesDir, "archive_${sanitized}_${timestamp}.json")
                file.writeText(gson.toJson(archive))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Write gigs to disk without triggering a background sync push
     * (used during restore to avoid immediately pushing the just-pulled data back).
     */
    fun saveGigsQuiet(gigs: List<Gig>): Boolean {
        return try {
            val file = File(context.filesDir, fileName)
            file.writeText(gson.toJson(gigs))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun archiveTour(tourName: String): Boolean {        return try {
            val gigs = loadGigs()
            val archivedDate = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH))
            val archive = TourArchive(
                tourName = tourName,
                archivedDate = archivedDate,
                gigs = gigs
            )
            val sanitized = tourName
                .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .trim()
                .replace(" ", "_")
                .take(50)
            val timestamp = System.currentTimeMillis()
            val archiveFile = File(context.filesDir, "archive_${sanitized}_${timestamp}.json")
            archiveFile.writeText(gson.toJson(archive))
            val saved = saveGigs(emptyList())
            // saveGigs already triggers backgroundSync for the empty active tour;
            // fire an extra push that includes the new archive list.
            backgroundSync(emptyList())
            saved
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
