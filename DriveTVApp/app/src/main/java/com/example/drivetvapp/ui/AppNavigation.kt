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
import coil.ImageLoader
import coil.compose.LocalImageLoader
import com.example.drivetvapp.auth.ServiceAccountAuth
import com.example.drivetvapp.drive.BrowseViewModel
import com.example.drivetvapp.drive.DriveRepository
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

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

    // Authenticated image loader for Drive thumbnails
    val imageLoader = remember {
        val authClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val token = runBlocking { auth.getAccessToken() }
                val authorized = original.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(authorized)
            }
            .build()

        ImageLoader.Builder(context)
            .okHttpClient(authClient)
            .crossfade(true)
            .build()
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalImageLoader provides imageLoader) {
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
}
