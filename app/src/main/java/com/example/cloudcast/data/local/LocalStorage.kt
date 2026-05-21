package com.example.cloudcast.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistencia sin Room ni anotaciones.
 * Usa SharedPreferences + Gson para guardar favoritos e historial.
 * Compatible con AGP 9.x / built-in Kotlin sin KSP ni kapt.
 */
class LocalStorage(context: Context) {

    private val prefs = context.getSharedPreferences("cloudcast_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _favIds = MutableStateFlow(loadFavIds())
    val favIds: Flow<Set<String>> = _favIds.asStateFlow()

    private val _historial = MutableStateFlow(loadHistorial())
    private val _descargas = MutableStateFlow(loadDescargas())

    val historial: Flow<List<HistorialEntry>> = _historial.asStateFlow()
    val descargas: Flow<List<DownloadRecord>> = _descargas.asStateFlow()

    private val _isDarkMode = MutableStateFlow<Boolean?>(loadDarkMode())
    val isDarkMode: Flow<Boolean?> = _isDarkMode.asStateFlow()

    private val _faceDownBehavior = MutableStateFlow(loadFaceDownBehavior())
    val faceDownBehavior: Flow<String> = _faceDownBehavior.asStateFlow()

    private fun loadFaceDownBehavior(): String =
        prefs.getString(KEY_FACE_DOWN_BEHAVIOR, "PAUSE") ?: "PAUSE"

    fun setFaceDownBehavior(behavior: String) {
        prefs.edit().putString(KEY_FACE_DOWN_BEHAVIOR, behavior).apply()
        _faceDownBehavior.value = behavior
    }

    fun getFaceDownBehavior(): String = _faceDownBehavior.value

    private fun loadFavIds(): Set<String> {
        val json = prefs.getString(KEY_FAV_IDS, null) ?: return emptySet()
        return gson.fromJson(json, object : TypeToken<Set<String>>() {}.type)
    }

    fun setFavorite(videoId: String, isFav: Boolean) {
        val current = _favIds.value.toMutableSet()
        if (isFav) current.add(videoId) else current.remove(videoId)
        prefs.edit().putString(KEY_FAV_IDS, gson.toJson(current)).apply()
        _favIds.value = current
    }

    fun isFavorite(videoId: String) = _favIds.value.contains(videoId)

    fun getFavIds(): Set<String> = _favIds.value

    private fun loadHistorial(): List<HistorialEntry> {
        val json = prefs.getString(KEY_HISTORIAL, null) ?: return emptyList()
        return gson.fromJson(json, object : TypeToken<List<HistorialEntry>>() {}.type)
    }

    fun addToHistorial(entry: HistorialEntry) {
        val current = _historial.value.toMutableList()
        current.removeAll { it.driveId == entry.driveId }
        current.add(0, entry)
        val trimmed = current.take(50)
        prefs.edit().putString(KEY_HISTORIAL, gson.toJson(trimmed)).apply()
        _historial.value = trimmed
    }

    fun clearHistorial() {
        prefs.edit().remove(KEY_HISTORIAL).apply()
        _historial.value = emptyList()
    }

    fun getHistorial(): List<HistorialEntry> = _historial.value

    private fun loadDescargas(): List<DownloadRecord> {
        val json = prefs.getString("descargas_v1", null) ?: return emptyList()
        return gson.fromJson(json, object : TypeToken<List<DownloadRecord>>() {}.type)
    }

    fun addDescarga(record: DownloadRecord) {
        val current = _descargas.value.toMutableList()
        current.removeAll { it.driveId == record.driveId }
        current.add(0, record)
        prefs.edit().putString("descargas_v1", gson.toJson(current)).apply()
        _descargas.value = current
    }

    fun removeDescarga(driveId: String) {
        val current = _descargas.value.toMutableList()
        current.removeAll { it.driveId == driveId }
        prefs.edit().putString("descargas_v1", gson.toJson(current)).apply()
        _descargas.value = current
    }

    private fun loadDarkMode(): Boolean? {
        return if (prefs.contains(KEY_DARK_MODE)) {
            prefs.getBoolean(KEY_DARK_MODE, false)
        } else null
    }

    fun toggleDarkMode(currentSystemTheme: Boolean) {
        val currentPref = _isDarkMode.value ?: currentSystemTheme
        val newTheme = !currentPref
        prefs.edit().putBoolean(KEY_DARK_MODE, newTheme).apply()
        _isDarkMode.value = newTheme
    }

    companion object {
        private const val KEY_FAV_IDS  = "fav_ids"
        private const val KEY_HISTORIAL = "historial"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_FACE_DOWN_BEHAVIOR = "face_down_behavior"

        @Volatile private var INSTANCE: LocalStorage? = null

        fun getInstance(context: Context): LocalStorage =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalStorage(context.applicationContext).also { INSTANCE = it }
            }
    }
}