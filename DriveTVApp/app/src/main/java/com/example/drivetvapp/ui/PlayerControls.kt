package com.example.drivetvapp.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.tv.material3.Surface
import androidx.tv.material3.ClickableSurfaceDefaults
import org.videolan.libvlc.MediaPlayer

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerControls(
    isVisible: Boolean,
    isPlaying: Boolean,
    currentTime: Long,
    totalTime: Long,
    subtitleTracks: Array<MediaPlayer.TrackDescription>,
    currentSubtitleTrackId: Int,
    onSubtitleTrackSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isShowingSubtitlesMenu by remember { mutableStateOf(false) }

    // Revert to primary controls when overlay closes
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            isShowingSubtitlesMenu = false
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            if (isShowingSubtitlesMenu) {
                // Subtitles Menu
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Select Subtitles", color = Color.White, fontSize = 24.sp, modifier = Modifier.padding(bottom = 16.dp))
                    
                    subtitleTracks.forEach { track ->
                        val isSelected = track.id == currentSubtitleTrackId
                        Surface(
                            onClick = { onSubtitleTrackSelected(track.id) },
                            modifier = Modifier.padding(vertical = 4.dp).width(200.dp),
                            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) Color.White.copy(alpha = 0.3f) else Color.Transparent,
                                focusedContainerColor = Color.White.copy(alpha = 0.8f)
                            )
                        ) {
                            Text(
                                text = track.name ?: "Track ${track.id}",
                                color = if (isSelected) Color.White else Color.LightGray,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        onClick = { isShowingSubtitlesMenu = false },
                        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Red.copy(alpha = 0.8f))
                    ) {
                        Text("Close", color = Color.White, modifier = Modifier.padding(12.dp))
                    }
                }
            } else {
                // Primary Controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause indicator
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isPlaying) "⏸" else "▶",
                                fontSize = 32.sp,
                                color = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(32.dp))
                        
                        // Subtitle Settings Gear
                        Surface(
                            onClick = { isShowingSubtitlesMenu = true },
                            modifier = Modifier.size(56.dp),
                            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = CircleShape),
                            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                focusedContainerColor = Color.White.copy(alpha = 0.5f)
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("⚙️ CC", fontSize = 18.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text(
                            text = formatTime(currentTime),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.width(16.dp))

                        // Simple progress bar
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(Color.DarkGray)
                        ) {
                            val progress = if (totalTime > 0) currentTime.toFloat() / totalTime.toFloat() else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(4.dp)
                                    .background(Color.Red)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = formatTime(totalTime),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
