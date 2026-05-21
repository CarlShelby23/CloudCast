package com.example.cloudcast.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.cloudcast.data.local.DownloadRecord
import com.example.cloudcast.data.local.HistorialEntry
import com.example.cloudcast.domain.model.VideoItem

enum class OrdenVideos(val label: String) {
    NOMBRE_AZ("Nombre A→Z"),
    NOMBRE_ZA("Nombre Z→A"),
    RECIENTES("Más recientes")
}

enum class TabFiltro(val label: String) {
    TODOS("Todos"),
    FAVORITOS("Favoritos"),
    HISTORIAL("Historial"),
    DESCARGAS("Descargas")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    videoList: List<VideoItem>,
    historial: List<HistorialEntry>,
    descargas: List<DownloadRecord>,
    onRemoveDescarga: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onSignOut: () -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onRefresh: () -> Unit,
    onClearHistory: () -> Unit,
    isRefreshing: Boolean,
    userEmail: String,
    userDisplayName: String,
    userPhotoUrl: String?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    faceDownBehavior: String,
    onSetFaceDownBehavior: (String) -> Unit
) {
    val context = LocalContext.current

    var showSignOutDialog  by remember { mutableStateOf(false) }
    var searchQuery        by remember { mutableStateOf("") }
    var searchActive       by remember { mutableStateOf(false) }
    var tabActiva          by remember { mutableStateOf(TabFiltro.TODOS) }
    var ordenActual        by remember { mutableStateOf(OrdenVideos.NOMBRE_AZ) }
    var showOrdenMenu      by remember { mutableStateOf(false) }
    var showPerfilDialog   by remember { mutableStateOf(false) }
    var showInfoForVideo   by remember { mutableStateOf<VideoItem?>(null) }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Cerrar sesión") },
            text  = { Text("¿Seguro que quieres cerrar sesión? Tendrás que volver a conectar tu cuenta de Google.") },
            confirmButton = {
                TextButton(onClick = { showSignOutDialog = false; onSignOut() }) {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showPerfilDialog) {
        AlertDialog(
            onDismissRequest = { showPerfilDialog = false },
            title = { Text("Mi cuenta", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(84.dp)
                    ) {
                        AsyncImage(
                            model = userPhotoUrl
                                ?: "https://ui-avatars.com/api/?name=${userDisplayName.replace(" ", "+")}&size=168&background=random",
                            contentDescription = "Foto de perfil",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(userDisplayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(userEmail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatChip(label = "Videos",    value = "${videoList.size}")
                        StatChip(label = "Favoritos", value = "${videoList.count { it.isFavorite }}")
                        StatChip(label = "Descargas", value = "${descargas.size}")
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    // Modo oscuro
                    SettingRow(
                        label = "Modo oscuro",
                        icon  = if (isDarkTheme) Icons.Rounded.DarkMode else Icons.Rounded.LightMode
                    ) {
                        Switch(checked = isDarkTheme, onCheckedChange = { onToggleTheme() })
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ScreenRotation,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Pantalla boca abajo",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = faceDownBehavior == "PAUSE",
                            onClick  = { onSetFaceDownBehavior("PAUSE") },
                            label    = { Text("Pausar") },
                            leadingIcon = if (faceDownBehavior == "PAUSE") {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = faceDownBehavior == "AUDIO_ONLY",
                            onClick  = { onSetFaceDownBehavior("AUDIO_ONLY") },
                            label    = { Text("Solo audio") },
                            leadingIcon = if (faceDownBehavior == "AUDIO_ONLY") {
                                { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))

                    // Cerrar sesión dentro del diálogo
                    TextButton(
                        onClick = { showPerfilDialog = false; showSignOutDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ExitToApp, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Cerrar sesión", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPerfilDialog = false }) {
                    Text("Cerrar", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    showInfoForVideo?.let { video ->
        AlertDialog(
            onDismissRequest = { showInfoForVideo = null },
            icon  = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Formato", video.mimeType?.substringAfterLast("/") ?: "Desconocido")
                    val sizeMb = video.sizeBytes?.let { it / (1024 * 1024) } ?: 0
                    InfoRow("Tamaño", "$sizeMb MB")
                    val durationMin = video.durationMillis?.let { it / 1000 / 60 } ?: 0
                    val durationSec = video.durationMillis?.let { (it / 1000) % 60 } ?: 0
                    InfoRow("Duración", "${durationMin}m ${durationSec}s")
                    InfoRow("Subido", video.createdTime?.take(10) ?: "Desconocido")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoForVideo = null }) { Text("Cerrar") }
            }
        )
    }

    val listaFiltrada = remember(videoList, historial, descargas, searchQuery, tabActiva, ordenActual) {
        val base: List<VideoItem> = when (tabActiva) {
            TabFiltro.TODOS      -> videoList
            TabFiltro.FAVORITOS  -> videoList.filter { it.isFavorite }
            TabFiltro.HISTORIAL  -> {
                val idsOrdenados = historial.map { it.driveId }
                idsOrdenados.mapNotNull { id -> videoList.find { it.id == id } }.distinct()
            }
            TabFiltro.DESCARGAS  -> {
                val idsOrdenados = descargas.map { it.driveId }
                idsOrdenados.mapNotNull { id -> videoList.find { it.id == id } }.distinct()
            }
        }
        val buscado = if (searchQuery.isBlank()) base
        else base.filter { it.title.contains(searchQuery, ignoreCase = true) }

        when (ordenActual) {
            OrdenVideos.NOMBRE_AZ -> buscado.sortedBy { it.title.lowercase() }
            OrdenVideos.NOMBRE_ZA -> buscado.sortedByDescending { it.title.lowercase() }
            OrdenVideos.RECIENTES -> buscado
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "CloudCast",
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.3).sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    actions = {
                        // Limpiar historial
                        AnimatedVisibility(
                            visible = tabActiva == TabFiltro.HISTORIAL && historial.isNotEmpty(),
                            enter = fadeIn(), exit = fadeOut()
                        ) {
                            IconButton(onClick = onClearHistory) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Limpiar historial")
                            }
                        }
                        // Ordenar
                        Box {
                            IconButton(onClick = { showOrdenMenu = true }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Ordenar")
                            }
                            DropdownMenu(expanded = showOrdenMenu, onDismissRequest = { showOrdenMenu = false }) {
                                OrdenVideos.entries.forEach { orden ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (ordenActual == orden)
                                                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                else
                                                    Spacer(Modifier.size(16.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(orden.label)
                                            }
                                        },
                                        onClick = { ordenActual = orden; showOrdenMenu = false }
                                    )
                                }
                            }
                        }
                        // Refrescar
                        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                            if (isRefreshing)
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refrescar")
                        }
                        // Avatar / perfil
                        IconButton(onClick = { showPerfilDialog = true }) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(32.dp)) {
                                AsyncImage(
                                    model = userPhotoUrl ?: "https://ui-avatars.com/api/?name=${userDisplayName.replace(" ", "+")}&size=64&background=random",
                                    contentDescription = "Perfil",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                )

                // Barra de búsqueda
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; searchActive = it.isNotBlank() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    placeholder = { Text("Buscar videos…", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon  = { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = {
                        AnimatedVisibility(searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; searchActive = false }) {
                                Icon(Icons.Rounded.Clear, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor   = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    )
                )

                // Tabs
                ScrollableTabRow(
                    selectedTabIndex = TabFiltro.entries.indexOf(tabActiva),
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    TabFiltro.entries.forEach { tab ->
                        Tab(
                            selected = tabActiva == tab,
                            onClick  = { tabActiva = tab },
                            text = {
                                Text(
                                    tab.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (tabActiva == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            }
        }
    ) { paddingValues ->

        when {
            tabActiva == TabFiltro.HISTORIAL && historial.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.History,
                    title = "Sin historial",
                    subtitle = "Los videos que reproduzcas aparecerán aquí",
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                )
                return@Scaffold
            }
            tabActiva == TabFiltro.DESCARGAS && descargas.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.Download,
                    title = "Sin descargas",
                    subtitle = "Descarga videos para verlos sin conexión",
                    actionLabel = "Explorar catálogo",
                    onAction = { tabActiva = TabFiltro.TODOS },
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                )
                return@Scaffold
            }
            tabActiva == TabFiltro.FAVORITOS && listaFiltrada.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.FavoriteBorder,
                    title = "Sin favoritos",
                    subtitle = "Toca el corazón en cualquier video para guardarlo",
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                )
                return@Scaffold
            }
            searchActive && listaFiltrada.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "Sin resultados",
                    subtitle = "No hay videos que coincidan con \"$searchQuery\"",
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                )
                return@Scaffold
            }
        }

        if (tabActiva == TabFiltro.DESCARGAS) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(paddingValues)
            ) {
                items(descargas, key = { it.driveId }) { record ->
                    DownloadVideoCard(
                        record  = record,
                        onClick = { onVideoClick(record.driveId) },
                        onRemove = { onRemoveDescarga(record.driveId) }
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(paddingValues)
            ) {
                items(listaFiltrada, key = { it.id }) { video ->
                    VideoCard(
                        video           = video,
                        onClick         = { onVideoClick(video.id) },
                        onFavoriteToggle = { onToggleFavorite(video) },
                        onInfoClick     = { showInfoForVideo = video },
                        onShare = {
                            val driveLink = "https://drive.google.com/file/d/${video.id}/view?usp=sharing"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Mira este video en CloudCast: ${video.title}\n$driveLink")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir video"))
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onInfoClick: () -> Unit,
    onShare: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.thumbnail
                    ?: "https://via.placeholder.com/300x400/0A1023/5EEAD4?text=Sin+Portada",
                contentDescription = "Portada de ${video.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                            startY = 180f
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (video.isFavorite) "Quitar favorito" else "Agregar favorito",
                    tint = if (video.isFavorite) Color(0xFFFB7185) else Color.White
                )
            }

            Box(modifier = Modifier.align(Alignment.TopStart)) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Compartir") },
                        onClick = { showMenu = false; onShare() },
                        leadingIcon = { Icon(Icons.Rounded.Share, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Detalles") },
                        onClick = { showMenu = false; onInfoClick() },
                        leadingIcon = { Icon(Icons.Rounded.Info, null) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                video.durationMillis?.let { ms ->
                    val min = ms / 1000 / 60
                    val sec = (ms / 1000) % 60
                    Spacer(Modifier.height(3.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "%d:%02d".format(min, sec),
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadVideoCard(
    record: DownloadRecord,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    var progress   by remember { mutableFloatStateOf(0f) }
    var status     by remember { mutableIntStateOf(android.app.DownloadManager.STATUS_PENDING) }
    var fileExists by remember { mutableStateOf(true) }

    LaunchedEffect(record.downloadId) {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        while (true) {
            val cursor = manager.query(android.app.DownloadManager.Query().setFilterById(record.downloadId))
            if (cursor != null && cursor.moveToFirst()) {
                status = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (total > 0) progress = downloaded.toFloat() / total.toFloat()
                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    val localUri = cursor.getString(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_LOCAL_URI))
                    if (localUri != null) {
                        val path = android.net.Uri.parse(localUri).path
                        if (path != null && !java.io.File(path).exists()) fileExists = false
                    }
                }
                cursor.close()
                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL || status == android.app.DownloadManager.STATUS_FAILED) break
            } else {
                fileExists = false
                break
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(enabled = fileExists && status == android.app.DownloadManager.STATUS_SUCCESSFUL) { onClick() },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = record.thumbnail
                    ?: "https://via.placeholder.com/300x400/0A1023/5EEAD4?text=Sin+Portada",
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = if (!fileExists) 0.3f else 1f
            )

            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                        startY = 180f
                    )
                )
            )
            Box(
                modifier = Modifier.fillMaxWidth().height(72.dp).align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.45f), Color.Transparent)))
            )

            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Rounded.Delete, "Eliminar descarga", tint = Color.White)
            }

            when {
                !fileExists -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.BrokenImage, "Archivo no encontrado", tint = Color(0xFFFB7185), modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Archivo eliminado", color = Color.White, fontSize = 12.sp)
                    }
                }
                status == android.app.DownloadManager.STATUS_RUNNING ||
                        status == android.app.DownloadManager.STATUS_PENDING -> {
                    Box(
                        Modifier.align(Alignment.Center).size(52.dp)
                            .background(Color.Black.copy(0.55f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            trackColor = Color.White.copy(0.25f)
                        )
                        if (progress > 0f) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                else -> {
                    Box(
                        Modifier.align(Alignment.Center).size(52.dp)
                            .clip(CircleShape).background(Color.White.copy(0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, "Reproducir", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }
            }

            Text(
                text = record.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SettingRow(label: String, icon: ImageVector, control: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        control()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAction, shape = RoundedCornerShape(14.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}