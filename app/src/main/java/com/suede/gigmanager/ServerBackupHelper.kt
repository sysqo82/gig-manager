package com.suede.gigmanager

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Checks the server for a backup after a fresh login and offers a full restore.
 * The check runs once per app process (guarded by [checkDone]).
 */
internal object ServerBackupHelper {

    @Volatile private var checkDone = false

    fun checkAndOffer(activity: AppCompatActivity, syncService: GigSyncService, onComplete: () -> Unit = {}) {
        if (checkDone) { onComplete(); return }
        checkDone = true

        Thread {
            val artistsResult = syncService.fetchArtists()
            if (artistsResult !is ApiResult.Success) { activity.runOnUiThread(onComplete); return@Thread }
            val serverArtists = artistsResult.data.artists
            if (serverArtists.isEmpty()) { activity.runOnUiThread(onComplete); return@Thread }

            // If any artist already has a local tour file, data is present — skip
            val hasLocalData = serverArtists.any {
                File(activity.filesDir, "tours_${it.id}.json").exists()
            }
            if (hasLocalData) { activity.runOnUiThread(onComplete); return@Thread }

            data class ArtistRestore(val artist: Artist, val data: SyncData)

            val restores = serverArtists.mapNotNull { artist ->
                when (val r = syncService.pullForArtist(artist.id)) {
                    is ApiResult.Success ->
                        if (r.data.gigs.isNotEmpty() || r.data.archives.isNotEmpty())
                            ArtistRestore(artist, r.data)
                        else null
                    else -> null
                }
            }
            if (restores.isEmpty()) { activity.runOnUiThread(onComplete); return@Thread }

            activity.runOnUiThread {
                // Activity may have been destroyed (e.g. back-pressed) while the
                // background check was running.  Skip the dialog and just navigate.
                if (activity.isFinishing || activity.isDestroyed) { onComplete(); return@runOnUiThread }

                val summary = restores.joinToString("\n") { (artist, data) ->
                    val gigCount  = data.gigs.size
                    val archCount = data.archives.size
                    val archNote  = if (archCount > 0) ", $archCount archived tour${if (archCount != 1) "s" else ""}" else ""
                    "\u2022 ${artist.name}: \"${data.tourName.ifEmpty { "Unnamed" }}\", $gigCount gig${if (gigCount != 1) "s" else ""}$archNote"
                }

                var completed = false
                fun complete() { if (!completed) { completed = true; onComplete() } }

                AlertDialog.Builder(activity)
                    .setTitle("Backup Found on Server")
                    .setMessage(
                        "A backup was found for ${restores.size} artist${if (restores.size != 1) "s" else ""}:\n\n$summary\n\n" +
                        "Do you want to restore everything?"
                    )
                    .setPositiveButton("Restore All") { _, _ ->
                        restores.forEach { (artist, data) ->
                            val dm = GigDataManager(activity, artist.id)
                            dm.savePreRestoreBackup()
                            dm.restoreFromServer(data.tourName, data.gigs, data.archives)
                        }
                        Toast.makeText(activity, "All data restored from server", Toast.LENGTH_LONG).show()
                        complete()
                    }
                    .setNegativeButton("Continue as New User") { _, _ -> complete() }
                    .show()
                    .setOnDismissListener { complete() }  // catches back-button / outside tap
            }
        }.start()
    }

    /** Call this on sign-out so the check re-runs on the next login. */
    fun reset() { checkDone = false }
}
