package com.example.drivetvapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.drivetvapp.drive.BrowseState
import com.example.drivetvapp.drive.BrowseViewModel
import com.example.drivetvapp.drive.DriveFile

import androidx.activity.compose.BackHandler

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowseScreen(
    browseViewModel: BrowseViewModel,
    onFileSelected: (String) -> Unit
) {
    val browseState by browseViewModel.browseState.collectAsState()
    val canGoBack by browseViewModel.canGoBack.collectAsState()

    // Handle back presses to go up the folder stack
    BackHandler(enabled = canGoBack) {
        browseViewModel.goBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Deep pure black for true minimalistic look
    ) {
        when (val state = browseState) {
            is BrowseState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading...",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 2.sp,
                        color = Color.DarkGray
                    )
                }
            }

            is BrowseState.FileList -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 56.dp, vertical = 40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.folderName == "root" || state.folderName.trim().isEmpty()) "DriveTV" else state.folderName,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 2.sp,
                            color = Color.White
                        )
                    }

                    if (state.files.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Empty folder",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Light,
                                color = Color(0xFF666666)
                            )
                        }
                    } else {
                        TvLazyVerticalGrid(
                            columns = TvGridCells.Fixed(4), // Slightly more dense modern grid
                            contentPadding = PaddingValues(start = 56.dp, end = 56.dp, bottom = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            items(state.files, key = { it.id }) { file ->
                                FileCard(file = file) {
                                    if (file.isFolder) {
                                        browseViewModel.navigateToFolder(file.id, file.name)
                                    } else {
                                        onFileSelected(file.id)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is BrowseState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Something went wrong",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Light,
                            color = Color(0xFFFF5252),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.tv.material3.Button(
                            onClick = { browseViewModel.loadFiles() },
                            colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = Color(0xFF222222))
                        ) {
                            Text("Retry", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FileCard(file: DriveFile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF141414), // Very dark gray, almost black
            focusedContainerColor = Color(0xFF2A2A2A) // Sleek gray highlight on focus
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Thumbnail or folder icon
            if (file.isFolder) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2B2B2B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "📁", fontSize = 36.sp) // Minimalist folder representation
                }
            } else {
                if (file.thumbnailLink != null) {
                    AsyncImage(
                        model = file.thumbnailLink,
                        contentDescription = file.name,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1F1F1F)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🎬", fontSize = 36.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = file.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (!file.isFolder && file.size > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatFileSize(file.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
