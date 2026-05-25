package com.example.drivetvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.drivetvapp.player.PlayerManager
import com.example.drivetvapp.ui.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlayerManager.Shared.initialize(applicationContext)
        setContent {
            AppNavigation()
        }
    }
}
