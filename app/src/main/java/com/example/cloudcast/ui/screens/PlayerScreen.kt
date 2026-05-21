package com.example.cloudcast.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import androidx.media3.datasource.DefaultDataSource

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    accessToken: String,
    videoTitle: String,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    faceDownBehavior: String = "PAUSE"
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(
                mapOf("Authorization" to "Bearer $accessToken")
            )

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(videoUrl))

            setMediaSource(mediaSource)
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    var isFullscreen by remember { mutableStateOf(false) }

    var isPlaying by remember { mutableStateOf(true) }

    var showOverlay by remember { mutableStateOf(true) }

    var isFaceDown by remember { mutableStateOf(false) }
    var accelZ by remember { mutableStateOf(0f) }
    var isProximityNear by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        val accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                accelZ = event.values[2]
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        val proximityListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val maxRange = proximitySensor?.maximumRange ?: 5f
                // Proximidad activa (objeto cerca) = valor <= 10% del rango máximo o == 0
                isProximityNear = event.values[0] <= (maxRange * 0.1f).coerceAtLeast(1f)
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        accelerometer?.let {
            sensorManager.registerListener(accelListener, it, SensorManager.SENSOR_DELAY_UI)
        }
        proximitySensor?.let {
            sensorManager.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager.unregisterListener(accelListener)
            sensorManager.unregisterListener(proximityListener)
        }
    }

    // Actualizar estado boca abajo: Z < -7 (≈ -9.8 m/s² al estar boca abajo) Y proximidad activa
    LaunchedEffect(accelZ, isProximityNear) {
        isFaceDown = accelZ < -7f && isProximityNear
    }

    // Aplicar comportamiento configurado cuando cambia el estado boca abajo
    LaunchedEffect(isFaceDown) {
        val layoutParams = activity?.window?.attributes
        when {
            isFaceDown -> when (faceDownBehavior) {
                "PAUSE" -> exoPlayer.pause()
                "AUDIO_ONLY" -> {
                    // Apagar pantalla manteniendo audio: bajar brillo a 0
                    layoutParams?.screenBrightness = 0f
                    activity?.window?.attributes = layoutParams
                }
            }
            else -> when (faceDownBehavior) {
                "PAUSE" -> { /* el usuario reanuda manualmente */ }
                "AUDIO_ONLY" -> {
                    // Restaurar brillo del sistema al volver a posición normal
                    layoutParams?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    activity?.window?.attributes = layoutParams
                }
            }
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            delay(500)
        }
    }

    LaunchedEffect(showOverlay) {
        if (showOverlay) {
            delay(3000)
            showOverlay = false
        }
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            exoPlayer.release()
            onBack()
        }
    }

    LaunchedEffect(isFullscreen) {
        activity?.let {
            val ctrl = WindowCompat.getInsetsController(it.window, it.window.decorView)
            if (isFullscreen) {
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            activity?.let {
                WindowCompat.getInsetsController(it.window, it.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true


                    setFullscreenButtonClickListener { clicked ->
                        isFullscreen = clicked
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showOverlay || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isFullscreen) isFullscreen = false
                    else { exoPlayer.release(); onBack() }
                }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }

                Text(
                    text = videoTitle,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = "Descargar video",
                        tint = Color.White
                    )
                }

                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.PauseCircle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}