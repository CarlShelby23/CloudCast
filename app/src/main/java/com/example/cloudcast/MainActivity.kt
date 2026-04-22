package com.example.cloudcast

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable // <- Importación vital para sobrevivir a la rotación
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var isUserLoggedIn by mutableStateOf(false)
    private var videoList by mutableStateOf<List<VideoItem>>(emptyList())
    private var isLoading by mutableStateOf(false)

    private var currentAccessToken by mutableStateOf<String?>(null)

    private var signInClient: GoogleSignInClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val existingAccount = GoogleSignIn.getLastSignedInAccount(this)
        if (existingAccount != null) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope("https://www.googleapis.com/auth/drive.readonly"))
                .build()
            signInClient = GoogleSignIn.getClient(this, gso)
            isUserLoggedIn = true
            fetchVideosFromDrive(this, existingAccount)
        }

        setContent {
            CloudCastTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    var selectedVideoUrl by rememberSaveable { mutableStateOf<String?>(null) }

                    BackHandler(enabled = selectedVideoUrl != null) {
                        selectedVideoUrl = null
                    }

                    if (!isUserLoggedIn) {
                        LoginScreen(
                            onLoginSuccess = { account, client ->
                                signInClient = client
                                isUserLoggedIn = true
                                fetchVideosFromDrive(this@MainActivity, account)
                            }
                        )
                    } else {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            if (selectedVideoUrl == null) {
                                LibraryScreen(
                                    videoList = videoList,
                                    onVideoClick = { clickedVideoId ->
                                        selectedVideoUrl = "https://www.googleapis.com/drive/v3/files/${clickedVideoId}?alt=media"
                                    },
                                    onSignOut = { signOut() }
                                )
                            } else {
                                PlayerScreen(
                                    videoUrl = selectedVideoUrl!!,
                                    accessToken = currentAccessToken ?: "",
                                    onBack = {
                                        selectedVideoUrl = null
                                    }
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
        }
    }

    private fun fetchVideosFromDrive(context: Context, account: GoogleSignInAccount) {
        isLoading = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val scopes = "oauth2:https://www.googleapis.com/auth/drive.readonly"
                val token = GoogleAuthUtil.getToken(context, account.account!!, scopes)

                currentAccessToken = token

                val response = RetrofitClient.instance.getDriveVideos("Bearer $token")
                val items = response.files.map {
                    VideoItem(it.id, it.name, it.thumbnailLink?.replace("=s220", "=s500"))
                }
                withContext(Dispatchers.Main) {
                    videoList = items
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e("CloudCast", "Error al cargar videos", e)
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }
}