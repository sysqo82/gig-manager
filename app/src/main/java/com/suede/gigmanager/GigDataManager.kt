package com.suede.gigmanager

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
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
                val normalized = gigs.map { gig ->
                    val parsed = parseGigDate(gig)
                    if (parsed != null) {
                        val cal = Calendar.getInstance()
                        cal.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
                        val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)
                        val standard = "$dayName, ${parsed.dayOfMonth}/${parsed.monthValue}/${parsed.year}"
                        if (gig.date != standard) gig.copy(date = standard) else gig
                    } else gig
                }
                val sorted = normalized.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
                if (normalized != gigs || normalized != sorted) {
                    saveGigs(sorted)
                }
                return sorted
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } else {
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
        gigs.add(gig)
        val sorted = gigs.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
        return saveGigs(sorted)
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
        val formats = listOf(
            "EEEE, d MMMM yyyy",
            "d MMMM yyyy",
            "EEEE, d/M/yyyy",
            "d/M/yyyy"
        )
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.ENGLISH)
                sdf.isLenient = true
                val date = sdf.parse(trimmed) ?: continue
                val cal = Calendar.getInstance()
                cal.time = date
                return LocalDate.of(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH)
                )
            } catch (_: Exception) { }
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
            val sorted = gigs.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
            val file = File(context.filesDir, fileName)
            file.writeText(gson.toJson(sorted))
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
