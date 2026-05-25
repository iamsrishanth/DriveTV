package com.example.drivetvapp.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.drivetvapp.auth.ServiceAccountAuth
import com.example.drivetvapp.player.PlayerManager
import androidx.media3.ui.CaptionStyleCompat
import android.graphics.Color as AndroidColor

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    subtitleUrls: List<String>,
    auth: ServiceAccountAuth,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { PlayerManager(context, auth) }
    val player = remember { playerManager.getPlayer() }

    DisposableEffect(streamUrl) {
        playerManager.playUrl(streamUrl, subtitleUrls)
        onDispose {
            playerManager.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 5000
                    setShowSubtitleButton(true)
                    
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()

                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                            val isDpadOrEnter = keyCode in listOf(
                                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                android.view.KeyEvent.KEYCODE_DPAD_UP,
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                                android.view.KeyEvent.KEYCODE_ENTER,
                                android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
                            )
                            
                            if (isDpadOrEnter && !isControllerFullyVisible) {
                                showController()
                                return@setOnKeyListener true // Wake up UI without triggering background actions
                            }
                        }
                        false // Pass event to ExoPlayer or standard Android TV focus handler
                    }

                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            AndroidColor.WHITE, // foregroundColor
                            AndroidColor.TRANSPARENT, // backgroundColor
                            AndroidColor.TRANSPARENT, // windowColor
                            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, // edgeType
                            AndroidColor.BLACK, // edgeColor
                            null // typeface
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
