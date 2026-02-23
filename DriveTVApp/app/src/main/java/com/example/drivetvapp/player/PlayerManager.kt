package com.example.drivetvapp.player

import android.content.Context
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class PlayerManager(private val context: Context) {

    var libVLC: LibVLC? = null
        private set
    var mediaPlayer: MediaPlayer? = null
        private set

    fun initialize() {
        if (libVLC != null) return

        val args = ArrayList<String>()
        args.add("-vvv")
        args.add("--drop-late-frames")
        args.add("--skip-frames")
        args.add("--rtsp-tcp")
        args.add("--network-caching=3000") // 3 second buffer for streaming

        libVLC = LibVLC(context, args)
        mediaPlayer = MediaPlayer(libVLC)
    }

    fun playUrl(url: String, accessToken: String, subtitleUrls: List<String> = emptyList()) {
        val vlc = libVLC ?: return
        val player = mediaPlayer ?: return

        // Stop current play if any
        player.stop()

        // Append access token to the URL so VLC can bypass needing HTTP Authorization headers
        // Google Drive API accepts access_token as a query param.
        val authenticatedUrl = if (url.contains("?")) {
            "$url&access_token=$accessToken"
        } else {
            "$url?access_token=$accessToken"
        }

        val media = Media(vlc, Uri.parse(authenticatedUrl))

        // Add subtitles
        // Priority 4 is typical for subtitle tracks in LibVLC slave loading
        subtitleUrls.forEachIndexed { index, subtitleUrl ->
            val authedSubtitleUrl = if (subtitleUrl.contains("?")) {
                "$subtitleUrl&access_token=$accessToken"
            } else {
                "$subtitleUrl?access_token=$accessToken"
            }
            media.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave(org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle, 4, authedSubtitleUrl))
        }

        player.media = media
        media.release() // MediaPlayer takes ownership

        player.play()
    }

    fun getSubtitleTracks(): Array<MediaPlayer.TrackDescription> {
        return mediaPlayer?.spuTracks ?: emptyArray()
    }

    fun setSubtitleTrack(trackId: Int) {
        mediaPlayer?.spuTrack = trackId
    }

    fun release() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        libVLC?.release()
        libVLC = null
    }
}
