package com.example.drivetvapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.drivetvapp.auth.ServiceAccountAuth
import com.example.drivetvapp.drive.BrowseViewModel
import com.example.drivetvapp.drive.DriveRepository

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()

    val auth = remember { ServiceAccountAuth(context) }
    val driveRepository = remember { DriveRepository(auth) }
    val browseViewModel: BrowseViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BrowseViewModel(driveRepository) as T
            }
        }
    )

    NavHost(navController = navController, startDestination = "browse") {
        composable("browse") {
            BrowseScreen(
                browseViewModel = browseViewModel,
                onFileSelected = { fileId ->
                    navController.navigate("player/$fileId")
                }
            )
        }

        composable("player/{fileId}") { backStackEntry ->
            val fileId = backStackEntry.arguments?.getString("fileId") ?: return@composable
            val streamUrl = browseViewModel.getStreamUrl(fileId)
            val subtitleUrls = browseViewModel.getSubtitleUrls(fileId)

            PlayerScreen(
                streamUrl = streamUrl,
                subtitleUrls = subtitleUrls,
                auth = auth,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
