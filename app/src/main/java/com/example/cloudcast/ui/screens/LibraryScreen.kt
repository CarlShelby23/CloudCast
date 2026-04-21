package com.example.cloudcast.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.cloudcast.domain.model.VideoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(videoList: List<VideoItem>, onVideoClick: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Mi Nube (CloudCast)") }) }) { paddingValues ->
        LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(12.dp), modifier = Modifier.padding(paddingValues)) {
            items(videoList) { video ->
                VideoCard(video = video, onClick = { onVideoClick(video.id) })
            }
        }
    }
}

@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().height(220.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp)) {
        Box {
            AsyncImage(model = video.thumbnail ?: "https://via.placeholder.com/300x400/2C2C2C/FFFFFF?text=Sin+Portada", contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f)), startY = 250f)))
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(0.5f)).align(Alignment.Center)) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White)
            }
            Text(video.title, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}