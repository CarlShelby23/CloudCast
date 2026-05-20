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

class MainActivity : ComponentActivity() {

    private var isUserLoggedIn by mutableStateOf(false)
    private var videoList by mutableStateOf<List<VideoItem>>(emptyList())
    private var historial by mutableStateOf<List<HistorialEntry>>(emptyList())
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
            storage.historial.collectLatest { entries -> historial = entries }
        }

        setContent {
            val isDarkModePref by storage.isDarkMode.collectAsState(initial = null)
            val systemTheme = isSystemInDarkTheme()
            val isDarkTheme = isDarkModePref ?: systemTheme
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
                            val url = "https://www.googleapis.com/drive/v3/files/${selectedVideoId}?alt=media"
                            PlayerScreen(
                                videoUrl = url,
                                accessToken = currentAccessToken ?: "",
                                videoTitle = selectedVideoTitle,
                                onBack = { selectedVideoId = null },
                                onDownload = {
                                    Toast.makeText(this@MainActivity, "Descarga no implementada: $selectedVideoTitle", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        else -> {
                            LibraryScreen(
                                videoList = videoList,
                                historial = historial,
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
                                onClearHistory = { storage.clearHistorial() }
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
}