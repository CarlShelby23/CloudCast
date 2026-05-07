package com.example.cloudcast

import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
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

class MainActivity : ComponentActivity() {

    private var isUserLoggedIn by mutableStateOf(false)
    private var videoList by mutableStateOf<List<VideoItem>>(emptyList())
    private var historial by mutableStateOf<List<HistorialEntry>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var isRefreshing by mutableStateOf(false)
    private var currentAccessToken by mutableStateOf<String?>(null)
    private var signInClient: GoogleSignInClient? = null
    private var currentAccount: GoogleSignInAccount? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storage = LocalStorage.getInstance(this)
        var isDarkModePref by mutableStateOf<Boolean?>(null)

        // Restaurar sesion automaticamente
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
            storage.historial.collectLatest { entries -> historial = entries }
        }
        lifecycleScope.launch {
            storage.isDarkMode.collectLatest { isDark -> isDarkModePref = isDark }
        }

        setContent {
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = isDarkModePref ?: systemDark

            CloudCastTheme(darkTheme = useDarkTheme) {
                val isOnline = rememberIsNetworkAvailable()
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    var selectedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
                    var selectedVideoTitle by rememberSaveable { mutableStateOf("") }

                    BackHandler(enabled = selectedVideoId != null) { selectedVideoId = null }

                    Box(modifier = Modifier.fillMaxSize()){
                        when {
                            !isUserLoggedIn -> {
                                // Login
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
                                // Reproductor
                                val url = "https://www.googleapis.com/drive/v3/files/${selectedVideoId}?alt=media"
                                PlayerScreen(
                                    videoUrl = url,
                                    accessToken = currentAccessToken ?: "",
                                    videoTitle = selectedVideoTitle,
                                    onBack = { selectedVideoId = null },
                                    onDownload = { 
                                        currentAccessToken?.let { token ->
                                            iniciarDescarga(this@MainActivity, selectedVideoId!!, selectedVideoTitle, token)
                                        }
                                    }
                                )
                            }
                            else -> {
                                // Biblioteca
                                LibraryScreen(
                                    videoList = videoList,
                                    historial = historial,
                                    isRefreshing = isRefreshing,
                                    userEmail = currentAccount?.email ?: "",
                                    userDisplayName = currentAccount?.displayName ?: "Usuario",
                                    userPhotoUrl = currentAccount?.photoUrl?.toString(),
                                    isDarkTheme = useDarkTheme,
                                    onToggleTheme = {
                                        storage.toggleDarkMode(systemDark)
                                    },
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
                                    onDownloadRequest = { videoId, title ->
                                        currentAccessToken?.let { token ->
                                            iniciarDescarga(this@MainActivity, videoId, title, token)
                                        }
                                    },
                                    onRefresh = {
                                        currentAccount?.let { acc ->
                                            isRefreshing = true
                                            fetchVideos(this@MainActivity, acc, storage, isRefresh = true)
                                        }
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = !isOnline,
                            enter = slideInVertically(animationSpec = tween(300)) { -it },
                            exit = slideOutVertically(animationSpec = tween(300)) { -it },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(10f) // Asegura que se dibuje por encima del reproductor y la biblioteca
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE53935)) // Un rojo agradable para alertas
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sin conexión a internet",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
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
                        isFavorite = it.id in favIds
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

    @Composable
    fun rememberIsNetworkAvailable(): Boolean {
        val context = LocalContext.current
        var isAvailable by remember { mutableStateOf(checkIfOnline(context)) }

        DisposableEffect(context) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { isAvailable = true }
                override fun onLost(network: Network) { isAvailable = false }
            }

            connectivityManager.registerDefaultNetworkCallback(callback)
            onDispose { connectivityManager.unregisterNetworkCallback(callback) }
        }
        return isAvailable
    }

    fun checkIfOnline(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun iniciarDescarga(context: Context, videoId: String, title: String, token: String) {
        val url = "https://www.googleapis.com/drive/v3/files/${videoId}?alt=media"
        
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(title)
            setDescription("Descargando video de CloudCast...")
            addRequestHeader("Authorization", "Bearer $token")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${title}.mp4")
            setAllowedOverMetered(true) // Permitir descarga con datos móviles
        }

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        
        Toast.makeText(context, "Descarga iniciada", Toast.LENGTH_SHORT).show()
    }
}
