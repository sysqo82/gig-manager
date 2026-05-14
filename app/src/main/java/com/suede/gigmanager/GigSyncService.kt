package com.suede.gigmanager

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * All network communication with the gig-manager sync API on the server.
 * Uses plain HttpURLConnection (no Retrofit) to keep the dependency footprint small.
 * Token is stored in EncryptedSharedPreferences so it survives app restarts.
 */
class GigSyncService(context: Context) {

    companion object {
        private const val TAG = "GigSyncService"

        /** Change this to the real public URL before shipping. */
        const val BASE_URL = "https://gigs.lockpc.co.uk"

        private const val PREF_FILE = "gig_sync_prefs"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_EMAIL = "account_email"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    private val gson = Gson()
    private val appContext = context.applicationContext

    // EncryptedSharedPreferences for secure token storage
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ---- Token / account helpers ----------------------------------------

    fun isLoggedIn(): Boolean = getToken() != null

    fun getEmail(): String? = securePrefs.getString(KEY_EMAIL, null)

    private fun getToken(): String? = securePrefs.getString(KEY_TOKEN, null)

    private fun saveCredentials(token: String, email: String) {
        securePrefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    fun clearCredentials() {
        securePrefs.edit().remove(KEY_TOKEN).remove(KEY_EMAIL).apply()
    }

    // ---- Network helpers -------------------------------------------------

    private fun openConnection(path: String, method: String = "GET"): HttpURLConnection {
        val url = URL("$BASE_URL$path")
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            getToken()?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }

    private fun post(path: String, body: Any): ApiResult<JsonObject> {
        return try {
            val conn = openConnection(path, "POST")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(gson.toJson(body)) }
            parseResponse(conn)
        } catch (e: Exception) {
            Log.e(TAG, "POST $path failed", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    private fun patch(path: String, body: Any): ApiResult<JsonObject> {
        return try {
            val conn = openConnection(path, "PATCH")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(gson.toJson(body)) }
            parseResponse(conn)
        } catch (e: Exception) {
            Log.e(TAG, "PATCH $path failed", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    private fun get(path: String): ApiResult<JsonObject> {
        return try {
            val conn = openConnection(path)
            parseResponse(conn)
        } catch (e: Exception) {
            Log.e(TAG, "GET $path failed", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    private fun delete(path: String): ApiResult<JsonObject> {
        return try {
            val conn = openConnection(path, "DELETE")
            parseResponse(conn)
        } catch (e: Exception) {
            Log.e(TAG, "DELETE $path failed", e)
            ApiResult.Error("Network error: ${e.message}")
        }
    }

    private fun parseResponse(conn: HttpURLConnection): ApiResult<JsonObject> {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.readText() ?: ""
        return if (code in 200..299) {
            val json = gson.fromJson(body, JsonObject::class.java)
            ApiResult.Success(json)
        } else {
            val msg = try {
                gson.fromJson(body, JsonObject::class.java)?.get("error")?.asString ?: "HTTP $code"
            } catch (_: Exception) { "HTTP $code" }
            ApiResult.Error(msg)
        }
    }

    // ---- Public API ------------------------------------------------------

    /**
     * Register a new account. Returns a token on success.
     * Must be called off the main thread.
     */
    fun register(email: String, password: String): ApiResult<Unit> {
        val result = post("/api/gigs/auth/register", mapOf("email" to email, "password" to password))
        return when (result) {
            is ApiResult.Success -> {
                val token = result.data.get("token")?.asString
                    ?: return ApiResult.Error("No token in response")
                saveCredentials(token, email)
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
        }
    }

    /**
     * Login with existing credentials. Returns a token on success.
     * Must be called off the main thread.
     */
    fun login(email: String, password: String): ApiResult<Unit> {
        val result = post("/api/gigs/auth/login", mapOf("email" to email, "password" to password))
        return when (result) {
            is ApiResult.Success -> {
                val token = result.data.get("token")?.asString
                    ?: return ApiResult.Error("No token in response")
                saveCredentials(token, email)
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
        }
    }

    /**
     * Push local data to server. Call this after every local save.
     * Must be called off the main thread.
     */
    fun push(tourName: String, gigs: List<Gig>, archives: List<TourArchive>): ApiResult<String> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        val body = mapOf("tourName" to tourName, "gigs" to gigs, "archives" to archives)
        return when (val result = post("/api/gigs/sync", body)) {
            is ApiResult.Success -> {
                val syncedAt = result.data.get("synced_at")?.asString ?: ""
                ApiResult.Success(syncedAt)
            }
            is ApiResult.Error -> result
        }
    }

    /**
     * Pull server data. Used for restore-after-wipe.
     * Returns a [SyncData] containing tourName, gigs, archives, and last_synced_at.
     * Must be called off the main thread.
     */
    fun pull(): ApiResult<SyncData> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        return when (val result = get("/api/gigs/sync")) {
            is ApiResult.Success -> {
                val json = result.data
                val tourName = json.get("tourName")?.asString ?: ""
                val gigsType = object : TypeToken<List<Gig>>() {}.type
                val archivesType = object : TypeToken<List<TourArchive>>() {}.type
                val gigs: List<Gig> = gson.fromJson(json.get("gigs"), gigsType) ?: emptyList()
                val archives: List<TourArchive> = gson.fromJson(json.get("archives"), archivesType) ?: emptyList()
                val syncedAt = json.get("last_synced_at")?.asString
                ApiResult.Success(SyncData(tourName, gigs, archives, syncedAt))
            }
            is ApiResult.Error -> result
        }
    }
    // ---- Artist + Deezer API ------------------------------------------------

    fun fetchArtists(): ApiResult<ArtistListResponse> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        return when (val result = get("/api/gigs/artists")) {
            is ApiResult.Success -> {
                val json = result.data
                val artistsType = object : TypeToken<List<Artist>>() {}.type
                val artists: List<Artist> = gson.fromJson(json.get("artists"), artistsType) ?: emptyList()
                val hasOrphaned = json.get("hasOrphanedTours")?.asBoolean ?: false
                ApiResult.Success(ArtistListResponse(artists, hasOrphaned))
            }
            is ApiResult.Error -> result
        }
    }

    fun createArtist(name: String, imageUrl: String?): ApiResult<Artist> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        val body = mapOf("name" to name, "imageUrl" to imageUrl)
        return when (val result = post("/api/gigs/artists", body)) {
            is ApiResult.Success -> {
                val json = result.data
                ApiResult.Success(
                    Artist(
                        id = json.get("id")?.asInt ?: 0,
                        name = json.get("name")?.asString ?: name,
                        imageUrl = json.get("imageUrl")?.takeIf { !it.isJsonNull }?.asString
                    )
                )
            }
            is ApiResult.Error -> result
        }
    }

    fun assignOrphanedTours(artistId: Int): ApiResult<Unit> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        return when (val result = post("/api/gigs/artists/assign-orphaned", mapOf("artistId" to artistId))) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> result
        }
    }

    fun pushForArtist(artistId: Int, tourName: String, gigs: List<Gig>, archives: List<TourArchive>): ApiResult<String> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        val body = mapOf("tourName" to tourName, "gigs" to gigs, "archives" to archives)
        return when (val result = post("/api/gigs/artists/$artistId/sync", body)) {
            is ApiResult.Success -> ApiResult.Success(result.data.get("synced_at")?.asString ?: "")
            is ApiResult.Error -> result
        }
    }

    fun pullForArtist(artistId: Int): ApiResult<SyncData> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        return when (val result = get("/api/gigs/artists/$artistId/sync")) {
            is ApiResult.Success -> {
                val json = result.data
                val tourName = json.get("tourName")?.asString ?: ""
                val gigsType = object : TypeToken<List<Gig>>() {}.type
                val archivesType = object : TypeToken<List<TourArchive>>() {}.type
                val gigs: List<Gig> = gson.fromJson(json.get("gigs"), gigsType) ?: emptyList()
                val archives: List<TourArchive> = gson.fromJson(json.get("archives"), archivesType) ?: emptyList()
                val syncedAt = json.get("last_synced_at")?.takeIf { !it.isJsonNull }?.asString
                ApiResult.Success(SyncData(tourName, gigs, archives, syncedAt))
            }
            is ApiResult.Error -> result
        }
    }

    fun updateArtistImage(artistId: Int, imageUrl: String?): ApiResult<Unit> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        val body = com.google.gson.JsonObject().apply { addProperty("imageUrl", imageUrl) }
        return when (val result = patch("/api/gigs/artists/$artistId", body)) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> result
        }
    }

    fun deleteArtist(artistId: Int): ApiResult<Unit> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        return when (val result = delete("/api/gigs/artists/$artistId")) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> result
        }
    }

    fun renameTour(artistId: Int, newName: String): ApiResult<Unit> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        return when (val result = patch("/api/gigs/artists/$artistId/tour/name", mapOf("tourName" to newName))) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Error -> result
        }
    }

    fun searchDeezer(query: String): ApiResult<List<DeezerArtist>> {
        if (!isLoggedIn()) return ApiResult.Error("Not logged in")
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return when (val result = get("/api/gigs/deezer?q=$encoded")) {
            is ApiResult.Success -> {
                val arr = result.data.getAsJsonArray("artists")
                val list = arr?.mapNotNull { el ->
                    val obj = el.asJsonObject
                    DeezerArtist(
                        id = obj.get("id")?.asLong ?: 0L,
                        name = obj.get("name")?.asString ?: return@mapNotNull null,
                        imageUrl = obj.get("imageUrl")?.takeIf { !it.isJsonNull }?.asString
                    )
                } ?: emptyList()
                ApiResult.Success(list)
            }
            is ApiResult.Error -> result
        }
    }
}

// ---- Sealed result type -------------------------------------------------

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}

// ---- Pull response payload ----------------------------------------------

data class SyncData(
    val tourName: String,
    val gigs: List<Gig>,
    val archives: List<TourArchive>,
    val lastSyncedAt: String?
)
