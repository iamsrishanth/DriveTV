package com.example.drivetvapp.drive

import com.example.drivetvapp.auth.ServiceAccountAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DriveRepository(private val auth: ServiceAccountAuth) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val videoMimeTypes = setOf(
        "video/mp4", "video/x-matroska", "video/webm",
        "video/avi", "video/mpeg", "video/quicktime",
        "video/x-msvideo", "video/3gpp", "video/x-flv",
        "video/ogg"
    )

    private val subtitleMimeTypes = setOf(
        "text/vtt", "application/x-subrip", "text/srt", "text/plain"
    )

    suspend fun listFiles(folderId: String = "root"): List<DriveFile> = withContext(Dispatchers.IO) {
        val accessToken = auth.getAccessToken()
        val allFiles = mutableListOf<DriveFile>()
        var pageToken: String? = null
        var pageCount = 0
        val maxPages = 5 // max 250 files

        do {
            pageCount++
            val files = fetchPage(accessToken, folderId, pageToken)
            allFiles.addAll(files)
            pageToken = nextPageToken
        } while (pageToken != null && pageCount < maxPages)

        allFiles
    }

    private var nextPageToken: String? = null

    private suspend fun fetchPage(
        accessToken: String,
        folderId: String,
        pageToken: String?
    ): List<DriveFile> = withContext(Dispatchers.IO) {

        val query = if (folderId == "root") {
            "sharedWithMe = true and trashed = false"
        } else {
            "'$folderId' in parents and trashed = false"
        }
        val fields = "nextPageToken,files(id,name,mimeType,size,thumbnailLink)"

        val url = buildString {
            append("https://www.googleapis.com/drive/v3/files")
            append("?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
            append("&fields=${java.net.URLEncoder.encode(fields, "UTF-8")}")
            append("&orderBy=name")
            append("&pageSize=50")
            if (pageToken != null) {
                append("&pageToken=${java.net.URLEncoder.encode(pageToken, "UTF-8")}")
            }
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Drive API returned HTTP ${resp.code}: ${resp.message}")
            }
            val body = resp.body ?: throw Exception("Drive API returned empty body")
            val responseBody = body.string()
            val json = JSONObject(responseBody)

            if (json.has("error")) {
                val errorMsg = json.getJSONObject("error").optString("message", responseBody)
                throw Exception("Drive API error: $errorMsg")
            }

            nextPageToken = if (json.has("nextPageToken")) json.getString("nextPageToken") else null

            val filesArray = json.optJSONArray("files") ?: return@withContext emptyList()
            val files = mutableListOf<DriveFile>()

            for (i in 0 until filesArray.length()) {
                val fileJson = filesArray.getJSONObject(i)
                val mimeType = fileJson.optString("mimeType", "")
                val isFolder = mimeType == "application/vnd.google-apps.folder"
                val isSubtitle = subtitleMimeTypes.contains(mimeType) || fileJson.optString("name").run { endsWith(".srt", true) || endsWith(".vtt", true) }

                if (isFolder || videoMimeTypes.contains(mimeType) || isSubtitle) {
                    files.add(
                        DriveFile(
                            id = fileJson.getString("id"),
                            name = fileJson.optString("name", "Unknown"),
                            mimeType = mimeType,
                            size = fileJson.optString("size", "0").toLongOrNull() ?: 0,
                            thumbnailLink = fileJson.optString("thumbnailLink", null),
                            isFolder = isFolder,
                            isSubtitle = isSubtitle
                        )
                    )
                }
            }
            files
        }
    }

    fun getStreamUrl(fileId: String): String {
        return "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
    }
}
