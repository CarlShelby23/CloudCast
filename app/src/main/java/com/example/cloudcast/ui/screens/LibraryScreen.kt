package com.example.cloudcast.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.cloudcast.data.local.HistorialEntry
import com.example.cloudcast.domain.model.VideoItem

// Opciones de orden
enum class OrdenVideos(val label: String) {
    NOMBRE_AZ("Nombre A→Z"),
    NOMBRE_ZA("Nombre Z→A"),
    RECIENTES("Más recientes")
}

// Filtros de pestaña
enum class TabFiltro(val label: String) {
    TODOS("Todos"),
    FAVORITOS("Favoritos"),
    HISTORIAL("Historial")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    videoList: List<VideoItem>,
    historial: List<HistorialEntry>,
    onVideoClick: (String) -> Unit,
    onSignOut: () -> Unit,
    onToggleFavorite: (VideoItem) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    userEmail: String,
    userDisplayName: String,
    userPhotoUrl: String?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onDownloadRequest: (String, String) -> Unit
) {
    val context = LocalContext.current

    // Estados

    var showSignOutDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    var tabActiva by remember { mutableStateOf(TabFiltro.TODOS) }

    var ordenActual by remember { mutableStateOf(OrdenVideos.NOMBRE_AZ) }
    var showOrdenMenu by remember { mutableStateOf(false) }

    var showPerfilDialog by remember { mutableStateOf(false) }

    // Diálogo para cerrar sesión

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Seguro que quieres cerrar sesión? Tendrás que volver a conectar tu cuenta de Google.") },
            confirmButton = {
                TextButton(onClick = { showSignOutDialog = false; onSignOut() }) {
                    Text("Cerrar sesión", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Diálogo de Perfil del usuario

    if (showPerfilDialog) {
        AlertDialog(
            onDismissRequest = { showPerfilDialog = false },
            title = { Text("Mi cuenta") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = userPhotoUrl ?: "https://ui-avatars.com/api/?name=${userDisplayName.replace(" ", "+")}&size=128",
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.size(80.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(userDisplayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(userEmail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("${videoList.size} videos en tu Drive", fontSize = 13.sp)
                    Text("${videoList.count { it.isFavorite }} favoritos", fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPerfilDialog = false }) { Text("Cerrar") }
            }
        )
    }

    val listaFiltrada = remember(videoList, historial, searchQuery, tabActiva, ordenActual) {
        val base: List<VideoItem> = when (tabActiva) {
            TabFiltro.TODOS -> videoList
            TabFiltro.FAVORITOS -> videoList.filter { it.isFavorite }      // UC16
            TabFiltro.HISTORIAL -> {                                         // UC17
                val idsOrdenados = historial.map { it.driveId }
                idsOrdenados.mapNotNull { id -> videoList.find { it.id == id } }.distinct()
            }
        }

        // Filtrar por búsqueda
        val buscado = if (searchQuery.isBlank()) base
        else base.filter { it.title.contains(searchQuery, ignoreCase = true) }

        // Aplicar orden
        when (ordenActual) {
            OrdenVideos.NOMBRE_AZ -> buscado.sortedBy { it.title.lowercase() }
            OrdenVideos.NOMBRE_ZA -> buscado.sortedByDescending { it.title.lowercase() }
            OrdenVideos.RECIENTES -> buscado  // el orden de Drive ya es cronológico
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("CloudCast") },
                    actions = {
                        IconButton(onClick = { showPerfilDialog = true }) {
                            Icon(Icons.Rounded.AccountCircle, contentDescription = "Perfil")
                        }
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
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = if (isDarkTheme) "Cambiar a tema claro" else "Cambiar a tema oscuro"
                            )
                        }
                        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                            if (isRefreshing)
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refrescar")
                        }
                        IconButton(onClick = { showSignOutDialog = true }) {
                            Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = "Cerrar sesión")
                        }
                    }
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; searchActive = it.isNotBlank() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    placeholder = { Text("Buscar videos...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        AnimatedVisibility(searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = ""; searchActive = false }) {
                                Icon(Icons.Rounded.Clear, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50)
                )

                TabRow(selectedTabIndex = TabFiltro.entries.indexOf(tabActiva)) {
                    TabFiltro.entries.forEach { tab ->
                        Tab(
                            selected = tabActiva == tab,
                            onClick = { tabActiva = tab },
                            text = { Text(tab.label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->

        if (tabActiva == TabFiltro.HISTORIAL && historial.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No has reproducido ningún video aún", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        if (listaFiltrada.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (searchActive) "🔍" else "📂", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (searchActive) "Sin resultados para \"$searchQuery\""
                               else "No hay videos en esta sección",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        video = video,
                        onClick = { onVideoClick(video.id) },
                        onFavoriteToggle = { onToggleFavorite(video) },
                        onShare = {
                            val videoLink = "https://drive.google.com/file/d/${video.id}/view?usp=sharing"

                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Enlace de CloudCast", videoLink)
                            clipboard.setPrimaryClip(clip)
                            
                            Toast.makeText(context, "Enlace copiado al portapapeles", Toast.LENGTH_SHORT).show()

                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TITLE, video.title)
                                putExtra(Intent.EXTRA_TEXT, "Mira este video en CloudCast: ${video.title}\n$videoLink")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir video"))
                        },
                        onDownload = { onDownloadRequest(video.id, video.title) }
                    )
                }
            }
        }
    }
}

// Tarjeta de video

@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = video.thumbnail ?: "https://via.placeholder.com/300x400/1C1C2E/FFFFFF?text=Sin+Portada",
                contentDescription = "Portada de ${video.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 200f
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, "Reproducir", tint = Color.White, modifier = Modifier.size(32.dp))
            }

            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (video.isFavorite) "Quitar favorito" else "Agregar favorito",
                    tint = if (video.isFavorite) Color(0xFFFF4081) else Color.White
                )
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Icon(Icons.Rounded.Share, "Compartir", tint = Color.White)
            }

            IconButton(
                onClick = onDownload,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp)
            ) {
                Icon(Icons.Rounded.Download, "Descargar", tint = Color.White)
            }

            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}
