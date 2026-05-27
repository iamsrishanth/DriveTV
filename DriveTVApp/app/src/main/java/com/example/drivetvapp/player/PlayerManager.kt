package com.example.drivetvapp.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.drivetvapp.auth.ServiceAccountAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File

@OptIn(UnstableApi::class)
class PlayerManager(private val context: Context, private val auth: ServiceAccountAuth) {

    private var player: ExoPlayer? = null

    private val _playerError = MutableStateFlow<PlaybackException?>(null)
    val playerError: StateFlow<PlaybackException?> = _playerError

    fun getPlayer(): ExoPlayer {
        if (player != null) return player!!

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val token = runBlocking { auth.getAccessToken() }
                val authorized = original.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(authorized)
            }
            .build()

        val upstreamFactory = OkHttpDataSource.Factory(okHttpClient)

        val cacheDataSourceFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(Shared.cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _playerError.value = error
            }
        })

        return player!!
    }

    fun playUrl(url: String, subtitleUrls: List<Pair<String, String>> = emptyList()) {
        player?.let {
            val mediaItemBuilder = MediaItem.Builder().setUri(url)
            
            val subtitleConfigs = subtitleUrls.mapIndexed { index, (subtitleUrl, subtitleName) ->
                val mimeType = if (subtitleName.endsWith(".vtt", ignoreCase = true)) {
                    androidx.media3.common.MimeTypes.TEXT_VTT
                } else {
                    androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                }
                
                // Only the first subtitle should be on by default. The rest should just be available in the menu.
                val selectionFlag = if (index == 0) androidx.media3.common.C.SELECTION_FLAG_DEFAULT else 0
                
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl))
                    .setMimeType(mimeType)
                    .setLanguage(detectSubtitleLanguage(subtitleName))
                    .setSelectionFlags(selectionFlag)
                    .build()
            }
            
            if (subtitleConfigs.isNotEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
            }
            
            it.setMediaItem(mediaItemBuilder.build())
            it.prepare()
            it.playWhenReady = true
        }
    }

    /**
     * Detects the language code from a subtitle filename.
     * e.g., "Movie.en.srt" → "en", "Movie.eng.srt" → "en", "Movie.srt" → "en" (default)
     */
    private fun detectSubtitleLanguage(fileName: String): String {
        val nameWithoutExt = fileName.substringBeforeLast('.')
        val lastDot = nameWithoutExt.lastIndexOf('.')
        if (lastDot > 0) {
            val suffix = nameWithoutExt.substring(lastDot + 1).lowercase()
            // Map common 3-letter codes to 2-letter
            val langMap = mapOf(
                "eng" to "en", "fre" to "fr", "spa" to "es", "ger" to "de",
                "jpn" to "ja", "kor" to "ko", "chi" to "zh", "por" to "pt",
                "ita" to "it", "rus" to "ru", "ara" to "ar", "hin" to "hi",
                "dut" to "nl", "swe" to "sv", "pol" to "pl", "tur" to "tr",
                "cze" to "cs", "rum" to "ro", "hun" to "hu", "fin" to "fi",
                "nor" to "no", "dan" to "da", "tha" to "th", "vie" to "vi"
            )
            if (suffix.length == 2 || suffix in langMap) {
                return langMap[suffix] ?: suffix
            }
        }
        return "en" // default
    }

    fun release() {
        player?.release()
        player = null
    }

    companion object Shared {
        @Volatile
        private var instance: SimpleCache? = null

        /** Maximum cache size in bytes. Default 500 MB — safe for TV boxes with limited storage. */
        const val MAX_CACHE_BYTES = 500_000_000L

        fun initialize(context: Context): SimpleCache {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val cacheDir = File(context.cacheDir, "exoplayer_cache")
                    val cacheEvictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
                    val databaseProvider = StandaloneDatabaseProvider(context)
                    SimpleCache(cacheDir, cacheEvictor, databaseProvider).also { instance = it }
                }
            }
        }

        val cache: SimpleCache
            get() = instance ?: throw IllegalStateException(
                "SimpleCache not initialized. Call PlayerManager.Shared.initialize(context) first."
            )
    }
}
