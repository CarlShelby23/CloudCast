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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

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
    val context  = LocalContext.current
    val activity = context as? Activity

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $accessToken"))
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
    var isPlaying    by remember { mutableStateOf(true) }
    var showOverlay  by remember { mutableStateOf(true) }

    LaunchedEffect(exoPlayer) {
        while (true) { isPlaying = exoPlayer.isPlaying; delay(500) }
    }

    LaunchedEffect(showOverlay) {
        if (showOverlay) { delay(3500); showOverlay = false }
    }

    var isFaceDown      by remember { mutableStateOf(false) }
    var accelZ          by remember { mutableStateOf(0f) }
    var isProximityNear by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sensorManager  = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        val accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) { accelZ = event.values[2] }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        val proximityListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val maxRange = proximitySensor?.maximumRange ?: 5f
                isProximityNear = event.values[0] <= (maxRange * 0.1f).coerceAtLeast(1f)
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        accelerometer?.let   { sensorManager.registerListener(accelListener,   it, SensorManager.SENSOR_DELAY_UI) }
        proximitySensor?.let { sensorManager.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_UI) }

        onDispose {
            sensorManager.unregisterListener(accelListener)
            sensorManager.unregisterListener(proximityListener)
        }
    }

    // Combinar señales: Z < -7 ≈ boca abajo, proximidad confirma que es sobre superficie
    LaunchedEffect(accelZ, isProximityNear) {
        isFaceDown = accelZ < -7f && isProximityNear
    }

    // Aplicar comportamiento según preferencia
    LaunchedEffect(isFaceDown) {
        val lp = activity?.window?.attributes
        when {
            isFaceDown -> when (faceDownBehavior) {
                "PAUSE"      -> exoPlayer.pause()
                "AUDIO_ONLY" -> {
                    lp?.screenBrightness = 0f
                    activity?.window?.attributes = lp
                }
            }
            else -> when (faceDownBehavior) {
                "PAUSE"      -> { /* usuario reanuda manualmente */ }
                "AUDIO_ONLY" -> {
                    lp?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    activity?.window?.attributes = lp
                }
            }
        }
    }

    BackHandler {
        if (isFullscreen) isFullscreen = false
        else { exoPlayer.release(); onBack() }
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
                // Restaurar brillo al salir
                val lp = it.window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.window.attributes = lp
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
                    setFullscreenButtonClickListener { clicked -> isFullscreen = clicked }
                    setOnClickListener { showOverlay = true }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showOverlay || !isPlaying,
            enter   = fadeIn(),
            exit    = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Volver
                    IconButton(onClick = {
                        if (isFullscreen) isFullscreen = false
                        else { exoPlayer.release(); onBack() }
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Color.White)
                    }

                    Text(
                        text = videoTitle,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    )

                    // Indicador boca abajo (visible cuando AUDIO_ONLY está activo pero pantalla es visible)
                    AnimatedVisibility(visible = isFaceDown && faceDownBehavior == "AUDIO_ONLY") {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Rounded.Headphones, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Solo audio", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    // Descargar
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Rounded.Download, "Descargar", tint = Color.White)
                    }

                    // Estado play/pause visual
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.PauseCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 4.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isFaceDown && faceDownBehavior == "AUDIO_ONLY",
            enter   = fadeIn(),
            exit    = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.82f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Solo audio activo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "La pantalla se apagará en un momento",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isFaceDown && faceDownBehavior == "PAUSE",
            enter   = fadeIn(),
            exit    = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.82f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Rounded.ScreenRotation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pausado · Gira el dispositivo para continuar", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}