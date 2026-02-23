package com.example.drivetvapp.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.example.drivetvapp.player.PlayerManager
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    subtitleUrls: List<String>,
    accessToken: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { PlayerManager(context) }
    
    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var totalTime by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    var subtitleTracks by remember { mutableStateOf<Array<MediaPlayer.TrackDescription>>(emptyArray()) }
    var currentSubtitleTrackId by remember { mutableIntStateOf(-1) }

    DisposableEffect(Unit) {
        playerManager.initialize()
        
        val listener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    isPlaying = true
                    subtitleTracks = playerManager.getSubtitleTracks()
                    currentSubtitleTrackId = playerManager.mediaPlayer?.spuTrack ?: -1
                }
                MediaPlayer.Event.Paused -> isPlaying = false
                MediaPlayer.Event.Stopped -> isPlaying = false
                MediaPlayer.Event.TimeChanged -> currentTime = event.timeChanged
                MediaPlayer.Event.LengthChanged -> totalTime = event.lengthChanged
                MediaPlayer.Event.ESAdded -> {
                    subtitleTracks = playerManager.getSubtitleTracks()
                }
            }
        }
        playerManager.mediaPlayer?.setEventListener(listener)
        
        playerManager.playUrl(streamUrl, accessToken, subtitleUrls)
        
        onDispose {
            playerManager.mediaPlayer?.setEventListener(null)
            playerManager.mediaPlayer?.detachViews()
            playerManager.release()
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    showControls = true
                    val player = playerManager.mediaPlayer ?: return@onKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> {
                            onBack()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (player.isPlaying) player.pause() else player.play()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            player.time = player.time + 10_000
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            player.time = maxOf(0, player.time - 10_000)
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).apply {
                    // Set focusable so this native view consumes D-pad events if needed
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    
                    playerManager.mediaPlayer?.attachViews(this, null, false, false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        PlayerControls(
            isVisible = showControls,
            isPlaying = isPlaying,
            currentTime = currentTime,
            totalTime = totalTime,
            subtitleTracks = subtitleTracks,
            currentSubtitleTrackId = currentSubtitleTrackId,
            onSubtitleTrackSelected = { trackId ->
                playerManager.setSubtitleTrack(trackId)
                currentSubtitleTrackId = trackId
                showControls = false // Close menu and overlay after selection
            }
        )
    }
}
