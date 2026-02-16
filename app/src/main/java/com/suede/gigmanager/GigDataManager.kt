package com.suede.gigmanager

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

class GigDataManager(private val context: Context) {
    
    private val fileName = "gigs_data_local.json"
    private val gson = Gson()

    fun loadGigs(): List<Gig> {
        // First check if we have a saved version in internal storage
        val file = File(context.filesDir, fileName)
        
        return if (file.exists()) {
            // Load from internal storage (user's modifications)
            try {
                val json = file.readText()
                if (json.isBlank()) return loadDefaultGigs()
                
                val type = object : TypeToken<List<Gig>>() {}.type
                val gigs: List<Gig>? = gson.fromJson(json, type)
                
                if (gigs == null) return loadDefaultGigs()

                val sorted = gigs.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
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
            val gigs: List<Gig>? = gson.fromJson(reader, type)
            reader.close()
            
            val result = gigs ?: emptyList()
            result.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
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
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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

    /**
     * Return the index of the next upcoming gig (today or later) that is not marked complete.
     * Returns null if there is no suitable upcoming gig.
     */
    fun getNextUpcomingGigIndex(): Int? {
        val gigs = loadGigs()
        val today = LocalDate.now()
        for ((idx, gig) in gigs.withIndex()) {
            val d = parseGigDate(gig)
            if (d != null && (d.isEqual(today) || d.isAfter(today)) && gig.isComplete != true) {
                return idx
            }
        }
        return null
    }
}
