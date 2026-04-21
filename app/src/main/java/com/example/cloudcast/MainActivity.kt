package com.example.cloudcast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.cloudcast.domain.model.VideoItem
import com.example.cloudcast.ui.screens.LibraryScreen
import com.example.cloudcast.ui.screens.LoginScreen
import com.example.cloudcast.ui.theme.CloudCastTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CloudCastTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var isUserLoggedIn by remember { mutableStateOf(false) }

                    val videosDePrueba = listOf(
                        VideoItem("1", "Proyecto Final.mp4", null),
                        VideoItem("2", "Tutorial de Android.mp4", null),
                        VideoItem("3", "Clase IHC.mkv", null)
                    )

                    if (!isUserLoggedIn) {
                        LoginScreen(
                            onLoginSuccess = { email, token ->
                                isUserLoggedIn = true
                            }
                        )
                    } else {
                        LibraryScreen(
                            videoList = videosDePrueba,
                            onVideoClick = { videoId ->
                            }
                        )
                    }
                }
            }
        }
    }
}