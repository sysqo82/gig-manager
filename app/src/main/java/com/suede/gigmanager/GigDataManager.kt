package com.suede.gigmanager

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class TourArchive(
    val tourName: String,
    val archivedDate: String,
    val gigs: List<Gig>
)

class GigDataManager(private val context: Context, private val artistId: Int? = null) {

    private val toursFileName = if (artistId != null) "tours_$artistId.json" else "tours_data.json"
    // Legacy file names kept for migration
    private val legacyGigsFile = if (artistId != null) "gigs_${artistId}_local.json" else "gigs_data_local.json"
    private val archivePrefix = if (artistId != null) "archive_${artistId}_" else "archive_"
    private val tourNameKey = if (artistId != null) "current_tour_name_$artistId" else "current_tour_name"

    private val gson = Gson()
    private val prefs by lazy { context.getSharedPreferences("gig_manager_prefs", Context.MODE_PRIVATE) }
    private val syncService by lazy { GigSyncService(context) }

    // ---- Tour CRUD -------------------------------------------------------

    fun loadTours(): List<Tour> {
        val file = File(context.filesDir, toursFileName)
        val tours: List<Tour> = if (file.exists()) {
            try {
                val type = object : TypeToken<List<Tour>>() {}.type
                gson.fromJson<List<Tour>>(file.readText(), type) ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } else {
            val migrated = migrateFromLegacyFormat()
            if (migrated.isNotEmpty()) {
                saveTours(migrated)
            }
            migrated
        }

        // Normalise dates and sort gigs by date within each tour
        return tours.map { tour ->
            val normalised = tour.gigs.map { gig ->
                val parsed = parseGigDate(gig)
                if (parsed != null) {
                    val cal = Calendar.getInstance()
                    cal.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
                    val dayName = SimpleDateFormat("EEEE", Locale.ENGLISH).format(cal.time)
                    val standard = "$dayName, ${parsed.dayOfMonth}/${parsed.monthValue}/${parsed.year}"
                    if (gig.date != standard) gig.copy(date = standard) else gig
                } else gig
            }
            val sorted = normalised.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
            if (sorted != tour.gigs) tour.copy(gigs = sorted) else tour
        }
    }

    fun saveTours(tours: List<Tour>): Boolean {
        return try {
            val file = File(context.filesDir, toursFileName)
            file.writeText(gson.toJson(tours))
            backgroundSync(tours)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun addTour(name: String): Tour? {
        return try {
            val tours = loadTours().toMutableList()
            val now = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH))
            val tour = Tour(id = UUID.randomUUID().toString(), name = name, createdDate = now)
            tours.add(0, tour) // newest first
            saveTours(tours)
            tour
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteTour(tourId: String): Boolean {
        val tours = loadTours().toMutableList()
        val removed = tours.removeAll { it.id == tourId }
        return if (removed) saveTours(tours) else false
    }

    fun getTour(tourId: String): Tour? = loadTours().find { it.id == tourId }

    fun renameTour(tourId: String, newName: String): Boolean {
        val tours = loadTours().toMutableList()
        val idx = tours.indexOfFirst { it.id == tourId }
        if (idx < 0) return false
        tours[idx] = tours[idx].copy(name = newName)
        return saveTours(tours)
    }

    // ---- Gig CRUD within a tour -----------------------------------------

    fun addGigToTour(tourId: String, gig: Gig): Boolean {
        val tours = loadTours().toMutableList()
        val idx = tours.indexOfFirst { it.id == tourId }
        if (idx < 0) return false
        val gigs = tours[idx].gigs.toMutableList()
        gigs.add(gig)
        val sorted = gigs.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
        tours[idx] = tours[idx].copy(gigs = sorted)
        return saveTours(tours)
    }

    fun updateGigInTour(tourId: String, gigIndex: Int, gig: Gig): Boolean {
        val tours = loadTours().toMutableList()
        val idx = tours.indexOfFirst { it.id == tourId }
        if (idx < 0) return false
        val gigs = tours[idx].gigs.toMutableList()
        if (gigIndex !in gigs.indices) return false
        gigs[gigIndex] = gig
        val sorted = gigs.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
        tours[idx] = tours[idx].copy(gigs = sorted)
        return saveTours(tours)
    }

    fun deleteGigFromTour(tourId: String, gigIndex: Int): Boolean {
        val tours = loadTours().toMutableList()
        val idx = tours.indexOfFirst { it.id == tourId }
        if (idx < 0) return false
        val gigs = tours[idx].gigs.toMutableList()
        if (gigIndex !in gigs.indices) return false
        gigs.removeAt(gigIndex)
        tours[idx] = tours[idx].copy(gigs = gigs)
        return saveTours(tours)
    }

    // ---- Migration from legacy format -----------------------------------

    private fun migrateFromLegacyFormat(): List<Tour> {
        val result = mutableListOf<Tour>()

        // Artist-scoped legacy gigs file (e.g. gigs_1_local.json)
        val gigsFile = File(context.filesDir, legacyGigsFile)

        // Non-artist-scoped legacy files used by pre-2.5 app (single-artist era)
        val legacyToursFile = File(context.filesDir, "tours_data.json")
        val legacyGigsGlobalFile = File(context.filesDir, "gigs_data_local.json")

        when {
            gigsFile.exists() -> {
                // Artist-scoped legacy gigs file — original path, no changes needed
                try {
                    val type = object : TypeToken<List<Gig>>() {}.type
                    val gigs: List<Gig> = gson.fromJson(gigsFile.readText(), type) ?: emptyList()
                    val tourName = prefs.getString(tourNameKey, "New Tour") ?: "New Tour"
                    result.add(Tour(
                        id = UUID.randomUUID().toString(),
                        name = tourName,
                        gigs = gigs.map { it.copy(isArchived = false) }
                    ))
                } catch (e: Exception) { /* skip */ }
            }

            artistId != null && legacyToursFile.exists() -> {
                // Pre-2.5 upgrade: user had a single non-artist-scoped tours file.
                // Migrate it to this artist and rename so a second artist doesn't inherit it.
                try {
                    val type = object : TypeToken<List<Tour>>() {}.type
                    val tours: List<Tour> = gson.fromJson(legacyToursFile.readText(), type) ?: emptyList()
                    result.addAll(tours)
                    legacyToursFile.renameTo(File(context.filesDir, "tours_data_migrated.json"))
                } catch (e: Exception) {
                    // Parse failed — fall through to empty tour below
                    val tourName = prefs.getString("current_tour_name", "New Tour") ?: "New Tour"
                    result.add(Tour(id = UUID.randomUUID().toString(), name = tourName))
                }
            }

            artistId != null && legacyGigsGlobalFile.exists() -> {
                // Pre-2.5 upgrade: very old single-artist gigs file.
                // Migrate it and rename so a second artist doesn't inherit it.
                try {
                    val type = object : TypeToken<List<Gig>>() {}.type
                    val gigs: List<Gig> = gson.fromJson(legacyGigsGlobalFile.readText(), type) ?: emptyList()
                    val tourName = prefs.getString("current_tour_name", "New Tour") ?: "New Tour"
                    result.add(Tour(
                        id = UUID.randomUUID().toString(),
                        name = tourName,
                        gigs = gigs.map { it.copy(isArchived = false) }
                    ))
                    legacyGigsGlobalFile.renameTo(File(context.filesDir, "gigs_data_migrated.json"))
                } catch (e: Exception) {
                    val tourName = prefs.getString("current_tour_name", "New Tour") ?: "New Tour"
                    result.add(Tour(id = UUID.randomUUID().toString(), name = tourName))
                }
            }

            else -> {
                // No legacy data at all — create an empty placeholder tour
                val tourName = prefs.getString(tourNameKey, "New Tour") ?: "New Tour"
                result.add(Tour(id = UUID.randomUUID().toString(), name = tourName))
            }
        }

        // Migrate archive files.
        // For artist-scoped context, first look for artist-specific archives; if none
        // exist (pre-2.5 upgrade), fall back to the non-artist-scoped archive files.
        var archiveFiles = context.filesDir.listFiles { f ->
            f.name.startsWith(archivePrefix) && f.name.endsWith(".json")
        } ?: emptyArray()

        if (archiveFiles.isEmpty() && artistId != null) {
            // Non-artist-scoped archives: named "archive_*" but NOT "archive_<digits>_*"
            archiveFiles = context.filesDir.listFiles { f ->
                f.name.startsWith("archive_") &&
                f.name.endsWith(".json") &&
                !f.name.matches(Regex("archive_\\d+_.*\\.json"))
            } ?: emptyArray()
        }

        for (file in archiveFiles.sortedByDescending { it.lastModified() }) {
            try {
                val archive = gson.fromJson(file.readText(), TourArchive::class.java)
                result.add(Tour(
                    id = UUID.randomUUID().toString(),
                    name = archive.tourName,
                    gigs = archive.gigs.map { it.copy(isArchived = true) },
                    createdDate = archive.archivedDate
                ))
            } catch (e: Exception) { /* skip */ }
        }

        return result
    }

    // ---- Backwards-compat helpers (used by AccountActivity / sync) ------

    fun getCurrentTourName(): String {
        val tours = loadTours()
        return tours.firstOrNull()?.name ?: (prefs.getString(tourNameKey, "New Tour") ?: "New Tour")
    }

    fun setCurrentTourName(name: String) {
        prefs.edit().putString(tourNameKey, name).apply()
        // Also rename first tour if it exists
        val tours = loadTours().toMutableList()
        if (tours.isNotEmpty()) {
            tours[0] = tours[0].copy(name = name)
            saveTours(tours)
        }
    }

    fun loadGigs(): List<Gig> {
        val tours = loadTours()
        return tours.firstOrNull()?.gigs?.filter { it.isArchived != true } ?: emptyList()
    }

    /** Compat: add a gig to the first (current) tour. */
    fun addGig(gig: Gig): Boolean {
        val tours = loadTours().toMutableList()
        return if (tours.isEmpty()) {
            val tourName = prefs.getString(tourNameKey, "New Tour") ?: "New Tour"
            val newTour = Tour(name = tourName, gigs = listOf(gig))
            tours.add(newTour)
            saveTours(tours)
        } else {
            addGigToTour(tours[0].id, gig)
        }
    }

    /** Compat: mark all active gigs of the first tour as archived (rename to archiveName), then prepend a new empty tour. */
    fun archiveTour(archiveName: String): Boolean {
        val tours = loadTours().toMutableList()
        if (tours.isEmpty()) return false
        // Archive current tour: rename + mark all gigs as archived
        val now = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu", java.util.Locale.ENGLISH))
        tours[0] = tours[0].copy(
            name = archiveName,
            gigs = tours[0].gigs.map { it.copy(isArchived = true) },
            createdDate = tours[0].createdDate ?: now
        )
        // Prepend a new empty tour to become the new current tour
        val newTour = Tour(name = prefs.getString(tourNameKey, "New Tour") ?: "New Tour", createdDate = now)
        tours.add(0, newTour)
        return saveTours(tours)
    }

    fun loadArchives(): List<TourArchive> {
        return loadTours().map { tour ->
            TourArchive(
                tourName = tour.name,
                archivedDate = tour.createdDate ?: "",
                gigs = tour.gigs
            )
        }
    }

    /** Compat: remove a single gig from a TourArchive (matched by name), returns updated TourArchive or null on failure. */
    fun deleteGigFromArchive(archive: TourArchive, gigIndex: Int): TourArchive? {
        val tours = loadTours().toMutableList()
        val idx = tours.indexOfFirst { it.name == archive.tourName }
        if (idx < 0) return null
        val gigs = tours[idx].gigs.toMutableList()
        if (gigIndex !in gigs.indices) return null
        gigs.removeAt(gigIndex)
        tours[idx] = tours[idx].copy(gigs = gigs)
        saveTours(tours)
        return TourArchive(archive.tourName, archive.archivedDate, gigs)
    }

    /** Compat: delete an entire tour matched by TourArchive name. */
    fun deleteArchive(archive: TourArchive): Boolean {
        val tours = loadTours().toMutableList()
        val removed = tours.removeAll { it.name == archive.tourName }
        return if (removed) saveTours(tours) else false
    }

    fun saveGigsQuiet(gigs: List<Gig>): Boolean {
        val tours = loadTours().toMutableList()
        val sorted = gigs.sortedWith(compareBy(nullsLast()) { parseGigDate(it) })
        return if (tours.isEmpty()) {
            val tourName = prefs.getString(tourNameKey, "New Tour") ?: "New Tour"
            val file = File(context.filesDir, toursFileName)
            file.writeText(gson.toJson(listOf(Tour(name = tourName, gigs = sorted))))
            true
        } else {
            tours[0] = tours[0].copy(gigs = sorted)
            val file = File(context.filesDir, toursFileName)
            file.writeText(gson.toJson(tours))
            true
        }
    }

    fun savePreRestoreBackup() {
        try {
            val tours = loadTours()
            val timestamp = System.currentTimeMillis()
            val file = File(context.filesDir, "pre_restore_$timestamp.json")
            file.writeText(gson.toJson(mapOf("tours" to tours)))
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun restoreArchives(archives: List<TourArchive>) {
        val restoredTours = archives.map { archive ->
            Tour(
                id = UUID.randomUUID().toString(),
                name = archive.tourName,
                gigs = archive.gigs.map { it.copy(isArchived = false) },
                createdDate = archive.archivedDate
            )
        }
        val file = File(context.filesDir, toursFileName)
        file.writeText(gson.toJson(restoredTours))
    }

    /**
     * Atomically writes the full server backup to local storage:
     * active tour first, then archived tours. Does NOT trigger a background sync.
     * Use this instead of calling restoreArchives + saveGigsQuiet separately,
     * which has a destructive interaction (saveGigsQuiet overwrites the first
     * archive tour's name and loses all other archives).
     */
    fun restoreFromServer(tourName: String, gigs: List<Gig>, archives: List<TourArchive>) {
        val activeTour = Tour(
            id = UUID.randomUUID().toString(),
            name = tourName.ifEmpty { "Restored Tour" },
            gigs = gigs
        )
        val archiveTours = archives.map { archive ->
            Tour(
                id = UUID.randomUUID().toString(),
                name = archive.tourName,
                gigs = archive.gigs.map { it.copy(isArchived = false) },
                createdDate = archive.archivedDate
            )
        }
        val file = File(context.filesDir, toursFileName)
        file.writeText(gson.toJson(listOf(activeTour) + archiveTours))
        prefs.edit().putString(tourNameKey, tourName.ifEmpty { "Restored Tour" }).apply()
    }

    // ---- Sync -----------------------------------------------------------

    private fun backgroundSync(tours: List<Tour>) {
        if (!syncService.isLoggedIn()) return
        Thread {
            try {
                // Map new Tour model to old SyncData format for server compatibility
                val currentTour = tours.firstOrNull()
                val tourName = currentTour?.name ?: getCurrentTourName()
                val activeGigs = currentTour?.gigs?.filter { it.isArchived != true } ?: emptyList()
                val archives = tours.drop(1).map { t ->
                    TourArchive(t.name, t.createdDate ?: "", t.gigs)
                } + (currentTour?.gigs?.filter { it.isArchived == true }?.let { archived ->
                    if (archived.isNotEmpty())
                        listOf(TourArchive("${tourName} (archived)", "", archived))
                    else emptyList()
                } ?: emptyList())

                val result = if (artistId != null)
                    syncService.pushForArtist(artistId, tourName, activeGigs, archives)
                else
                    syncService.push(tourName, activeGigs, archives)

                when (result) {
                    is ApiResult.Success -> Log.d("GigSync", "Auto-sync OK: ${result.data}")
                    is ApiResult.Error -> {
                        Log.e("GigSync", "Auto-sync failed: ${result.message}")
                        // 409 means the server has data we don't (e.g. fresh install or
                        // assign-orphaned not yet run). Pull instead of pushing so local
                        // state reflects whatever the server already has.
                        if (result.message.contains("Sync rejected", ignoreCase = true) && artistId != null) {
                            val pull = syncService.pullForArtist(artistId)
                            if (pull is ApiResult.Success) {
                                val data = pull.data
                                if (data.gigs.isNotEmpty() || data.archives.isNotEmpty()) {
                                    savePreRestoreBackup()
                                    restoreFromServer(data.tourName, data.gigs, data.archives)
                                    Log.d("GigSync", "Auto-sync 409: restored ${data.gigs.size} gigs from server")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("GigSync", "Auto-sync exception", e)
            }
        }.start()
    }

    // ---- Date parsing ---------------------------------------------------

    private fun parseGigDate(gig: Gig): LocalDate? {
        val raw = gig.date ?: return null
        val trimmed = raw.trim()
        val formats = listOf("EEEE, d MMMM yyyy", "d MMMM yyyy", "EEEE, d/M/yyyy", "d/M/yyyy")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.ENGLISH)
                sdf.isLenient = true
                val date = sdf.parse(trimmed) ?: continue
                val cal = Calendar.getInstance()
                cal.time = date
                return LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            } catch (_: Exception) { }
        }
        return null
    }
}
