package com.example.drivetvapp.auth

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

class ServiceAccountAuth(context: Context) {

    private val client = OkHttpClient()
    private val mutex = Mutex()

    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0

    private val clientEmail: String
    private val privateKeyPem: String
    private val tokenUri: String

    init {
        val raw = context.resources.openRawResource(
            context.resources.getIdentifier("credentials", "raw", context.packageName)
        )
        val json = JSONObject(raw.bufferedReader().use { it.readText() })
        clientEmail = json.getString("client_email")
        privateKeyPem = json.getString("private_key")
        tokenUri = json.getString("token_uri")
    }

    suspend fun getAccessToken(): String = mutex.withLock {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiresAt - 60_000) {
            return cachedToken!!
        }
        return withContext(Dispatchers.IO) {
            val jwt = createSignedJwt()
            val token = exchangeJwtForToken(jwt)
            cachedToken = token
            tokenExpiresAt = System.currentTimeMillis() + 3600_000 // 1 hour
            token
        }
    }

    private fun createSignedJwt(): String {
        val now = System.currentTimeMillis() / 1000
        val exp = now + 3600

        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""".toByteArray())

        val claims = JSONObject().apply {
            put("iss", clientEmail)
            put("scope", "https://www.googleapis.com/auth/drive.readonly")
            put("aud", tokenUri)
            put("iat", now)
            put("exp", exp)
        }
        val payload = base64Url(claims.toString().toByteArray())

        val signInput = "$header.$payload"
        val signature = signRsa256(signInput.toByteArray(), privateKeyPem)

        return "$signInput.${base64Url(signature)}"
    }

    private fun exchangeJwtForToken(jwt: String): String {
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build()

        val request = Request.Builder()
            .url(tokenUri)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body!!.string()
        val json = JSONObject(responseBody)

        if (json.has("error")) {
            throw Exception("Token exchange failed: ${json.optString("error_description", responseBody)}")
        }

        return json.getString("access_token")
    }

    private fun signRsa256(data: ByteArray, pemKey: String): ByteArray {
        val stripped = pemKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\n", "")
            .trim()

        val keyBytes = Base64.decode(stripped, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)

        return Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }
    }

    private fun base64Url(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
