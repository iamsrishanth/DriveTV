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
import java.util.concurrent.TimeUnit

class ServiceAccountAuth(context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()

    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0

    private val clientEmail: String
    private val privateKeyPem: String
    private val tokenUri: String

    init {
        val resId = context.resources.getIdentifier("credentials", "raw", context.packageName)
        if (resId == 0) {
            throw IllegalStateException(
                "credentials.json not found in res/raw/. " +
                "Place your service account key at app/src/main/res/raw/credentials.json"
            )
        }
        val raw = context.resources.openRawResource(resId)
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
            val (token, expiresIn) = exchangeJwtForToken(jwt)
            cachedToken = token
            tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
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

    private fun exchangeJwtForToken(jwt: String): Pair<String, Long> {
        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", jwt)
            .build()

        val request = Request.Builder()
            .url(tokenUri)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw Exception("Token exchange failed: HTTP ${resp.code} ${resp.message}")
            }
            val respBody = resp.body ?: throw Exception("Token exchange returned empty body")
            val responseBody = respBody.string()
            val json = JSONObject(responseBody)

            if (json.has("error")) {
                throw Exception("Token exchange failed: ${json.optString("error_description", responseBody)}")
            }

            val token = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 3600L)
            return token to expiresIn
        }
    }

    private fun signRsa256(data: ByteArray, pemKey: String): ByteArray {
        val stripped = pemKey
            .replace("[REDACTED PRIVATE KEY]", "")
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
