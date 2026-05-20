package com.example.cloudcast

import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.cloudcast.data.local.HistorialEntry
import com.example.cloudcast.data.local.LocalStorage
import com.example.cloudcast.data.remote.RetrofitClient
import com.example.cloudcast.domain.model.VideoItem
import com.example.cloudcast.ui.screens.LibraryScreen
import com.example.cloudcast.ui.screens.LoginScreen
import com.example.cloudcast.ui.screens.PlayerScreen
import com.example.cloudcast.ui.theme.CloudCastTheme
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import com.example.cloudcast.data.local.DownloadRecord

class MainActivity : ComponentActivity() {

    private var isUserLoggedIn by mutableStateOf(false)
    private var videoList by mutableStateOf<List<VideoItem>>(emptyList())
    private var historial by mutableStateOf<List<HistorialEntry>>(emptyList())
    private var descargas by mutableStateOf<List<DownloadRecord>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var isRefreshing by mutableStateOf(false)
    private var currentAccessToken by mutableStateOf<String?>(null)
    private var signInClient: GoogleSignInClient? = null
    private var currentAccount: GoogleSignInAccount? = null

    // Variable para controlar PiP
    private var isPlayingVideo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storage = LocalStorage.getInstance(this)

        val existingAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (existingAccount != null) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope("https://www.googleapis.com/auth/drive.readonly"))
                .build()
            signInClient = GoogleSignIn.getClient(this, gso)
            currentAccount = existingAccount
            isUserLoggedIn = true
            fetchVideos(this, existingAccount, storage)
        }

        lifecycleScope.launch {
            launch {
                storage.historial.collectLatest { entries -> historial = entries }
            }
            launch {
                storage.descargas.collectLatest { entries -> descargas = entries }
            }
        }

        setContent {
            val isDarkModePref by storage.isDarkMode.collectAsState(initial = null)
            val systemTheme = isSystemInDarkTheme()
            val isDarkTheme = isDarkModePref ?: systemTheme
            val context = LocalContext.current
            val isConnected by context.observeConnectivityAsFlow().collectAsState(initial = true)

            LaunchedEffect(isConnected) {
                if (!isConnected) {
                    Toast.makeText(context, "Sin conexión a internet. Revisa tu red", Toast.LENGTH_LONG).show()
                }
            }
            CloudCastTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    var selectedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
                    var selectedVideoTitle by rememberSaveable { mutableStateOf("") }

                    // Actualizamos el estado de reproducción para PiP
                    isPlayingVideo = selectedVideoId != null

                    BackHandler(enabled = selectedVideoId != null) { selectedVideoId = null }

                    when {
                        !isUserLoggedIn -> {
                            LoginScreen(
                                onLoginSuccess = { account, client ->
                                    signInClient = client
                                    currentAccount = account
                                    isUserLoggedIn = true
                                    fetchVideos(this@MainActivity, account, storage)
                                }
                            )
                        }
                        isLoading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        selectedVideoId != null -> {
                            val record = descargas.find { it.driveId == selectedVideoId }
                            val localUri = record?.let { getLocalVideoUri(this@MainActivity, it.downloadId) }

                            val url = localUri ?: "https://www.googleapis.com/drive/v3/files/${selectedVideoId}?alt=media"

                            PlayerScreen(
                                videoUrl = url,
                                accessToken = currentAccessToken ?: "",
                                videoTitle = selectedVideoTitle,
                                onBack = { selectedVideoId = null },
                                onDownload = {
                                    currentAccessToken?.let { token ->
                                        val thumbnail = videoList.find { it.id == selectedVideoId }?.thumbnail
                                        download(
                                            context = this@MainActivity,
                                            videoId = selectedVideoId!!,
                                            title = selectedVideoTitle,
                                            thumbnail = thumbnail,
                                            token = token,
                                            storage = storage
                                        )
                                    } ?: Toast.makeText(this@MainActivity, "Error de sesión", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        else -> {
                            LibraryScreen(
                                videoList = videoList,
                                historial = historial,
                                descargas = descargas,
                                isRefreshing = isRefreshing,
                                userEmail = currentAccount?.email ?: "",
                                userDisplayName = currentAccount?.displayName ?: "Usuario",
                                userPhotoUrl = currentAccount?.photoUrl?.toString(),
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = { storage.toggleDarkMode(systemTheme) },
                                onVideoClick = { clickedId ->
                                    val video = videoList.find { it.id == clickedId }
                                    selectedVideoId = clickedId
                                    selectedVideoTitle = video?.title ?: ""
                                    video?.let {
                                        storage.addToHistorial(
                                            HistorialEntry(driveId = it.id, title = it.title, thumbnailUrl = it.thumbnail)
                                        )
                                    }
                                },
                                onSignOut = { signOut() },
                                onToggleFavorite = { video ->
                                    val newFav = !video.isFavorite
                                    storage.setFavorite(video.id, newFav)
                                    videoList = videoList.map {
                                        if (it.id == video.id) it.copy(isFavorite = newFav) else it
                                    }
                                },
                                onRefresh = {
                                    currentAccount?.let { acc ->
                                        isRefreshing = true
                                        fetchVideos(this@MainActivity, acc, storage, isRefresh = true)
                                    }
                                },
                                onClearHistory = { storage.clearHistorial() },
                                onRemoveDescarga = { storage.removeDescarga(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayingVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder().build()
            enterPictureInPictureMode(params)
        }
    }

    private fun signOut() {
        signInClient?.signOut()?.addOnCompleteListener {
            isUserLoggedIn = false
            videoList = emptyList()
            signInClient = null
            currentAccessToken = null
            currentAccount = null
        }
    }

    private fun fetchVideos(
        context: Context,
        account: GoogleSignInAccount,
        storage: LocalStorage,
        isRefresh: Boolean = false
    ) {
        if (!isRefresh) isLoading = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val scopes = "oauth2:https://www.googleapis.com/auth/drive.readonly"
                val token = GoogleAuthUtil.getToken(context, account.account!!, scopes)
                currentAccessToken = token

                val response = RetrofitClient.instance.getDriveVideos("Bearer $token")
                val favIds = storage.getFavIds()

                val items = response.files.map {
                    VideoItem(
                        id = it.id,
                        title = it.name,
                        thumbnail = it.thumbnailLink?.replace("=s220", "=s500"),
                        mimeType = it.mimeType,
                        isFavorite = it.id in favIds,
                        sizeBytes = it.size?.toLongOrNull(),
                        createdTime = it.createdTime,
                        durationMillis = it.videoMediaMetadata?.durationMillis
                    )
                }

                withContext(Dispatchers.Main) {
                    videoList = items
                    isLoading = false
                    isRefreshing = false
                }
            } catch (e: Exception) {
                Log.e("CloudCast", "Error al cargar videos", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }

    fun Context.observeConnectivityAsFlow(): Flow<Boolean> = callbackFlow {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onUnavailable() { trySend(false) }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        val activeNetwork = connectivityManager.activeNetwork
        val isConnected = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        trySend(isConnected)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    fun download(context: Context, videoId: String, title: String, thumbnail: String?, token: String, storage: LocalStorage) {

        val TAG = "CloudCastDownload"
        Log.d(TAG, "--- Iniciando proceso de descarga ---")
        Log.d(TAG, "Video ID: $videoId")
        Log.d(TAG, "Título original: $title")

        try {
            val url = "https://www.googleapis.com/drive/v3/files/${videoId}?alt=media"
            Log.d(TAG, "URL de descarga generada: $url")
            Log.d(TAG, "Token disponible: ${token.isNotEmpty()}")

            val fileName = "${title.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.mp4"
            Log.d(TAG, "Nombre de archivo limpio: $fileName")

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(title)
                setDescription("Descargando video de CloudCast...")
                addRequestHeader("Authorization", "Bearer $token")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                val dir = Environment.DIRECTORY_MOVIES
                val subPath = "CloudCast/$fileName"
                setDestinationInExternalPublicDir(dir, subPath)
                Log.d(TAG, "Destino configurado en: $dir/$subPath")

                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }


            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            Log.d(TAG, "DownloadManager obtenido correctamente")

            val downloadId = manager.enqueue(request)
            Log.d(TAG, "¡Éxito! Descarga encolada con el ID del sistema: $downloadId")
            storage.addDescarga(DownloadRecord(videoId, title, thumbnail, downloadId))

            Toast.makeText(context, "Descarga iniciada", Toast.LENGTH_SHORT).show()

        } catch (e: SecurityException) {
            Log.e(TAG, "Error de SEGURIDAD. ¿Falta el permiso WRITE_EXTERNAL_STORAGE en el AndroidManifest.xml?", e)
            Toast.makeText(context, "Error de permisos para guardar el video", Toast.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Error de ARGUMENTOS. Es posible que la ruta de destino o la URL sean inválidas.", e)
            Toast.makeText(context, "Error en la ruta del archivo", Toast.LENGTH_LONG).show()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error de ESTADO. El DownloadManager podría estar deshabilitado en este dispositivo.", e)
            Toast.makeText(context, "El gestor de descargas no está disponible", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error GENERAL inesperado al encolar la descarga.", e)
            Toast.makeText(context, "Error desconocido al descargar", Toast.LENGTH_LONG).show()
        }
    }
}

fun getLocalVideoUri(context: Context, downloadId: Long): String? {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
    if (cursor != null && cursor.moveToFirst()) {
        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            cursor.close()
            return localUri
        }
        cursor.close()
    }
    return null
}