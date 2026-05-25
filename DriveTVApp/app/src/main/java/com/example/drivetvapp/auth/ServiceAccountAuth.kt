     1|     1|package com.example.drivetvapp.auth
     2|     2|
     3|     3|import android.content.Context
     4|     4|import android.util.Base64
     5|     5|import kotlinx.coroutines.Dispatchers
     6|     6|import kotlinx.coroutines.sync.Mutex
     7|     7|import kotlinx.coroutines.sync.withLock
     8|     8|import kotlinx.coroutines.withContext
     9|     9|import okhttp3.FormBody
    10|    10|import okhttp3.OkHttpClient
    11|    11|import okhttp3.Request
    12|    12|import org.json.JSONObject
    13|    13|import java.security.KeyFactory
    14|    14|import java.security.Signature
    15|    15|import java.security.spec.PKCS8EncodedKeySpec
    16|    16|import java.util.concurrent.TimeUnit
    17|    17|
    18|    18|class ServiceAccountAuth(private val context: Context) {
    19|    19|
    20|    20|    private val client = OkHttpClient.Builder()
    21|    21|        .connectTimeout(15, TimeUnit.SECONDS)
    22|    22|        .readTimeout(30, TimeUnit.SECONDS)
    23|    23|        .writeTimeout(30, TimeUnit.SECONDS)
    24|    24|        .callTimeout(60, TimeUnit.SECONDS)
    25|    25|        .build()
    26|    26|
    27|    27|    private val mutex = Mutex()
    28|    28|
    29|    29|    private var cachedToken: String? = null
    30|    30|    private var tokenExpiresAt: Long = 0
    31|    31|
    32|    32|    private data class Credentials(
    33|    33|        val clientEmail: String,
    34|    34|        val privateKeyPem: String,
    35|    35|        val tokenUri: String
    36|    36|    )
    37|    37|
    38|    38|    // Lazy-loaded to avoid blocking the composition thread with disk I/O
    39|    39|    private val credentials: Credentials by lazy {
    40|    40|        val resId = context.resources.getIdentifier("credentials", "raw", context.packageName)
    41|    41|        if (resId == 0) {
    42|    42|            throw IllegalStateException(
    43|    43|                "credentials.json not found in res/raw/. " +
    44|    44|                "Place your service account key at app/src/main/res/raw/credentials.json"
    45|    45|            )
    46|    46|        }
    47|    47|        val raw = context.resources.openRawResource(resId)
    48|    48|        val json = JSONObject(raw.bufferedReader().use { it.readText() })
    49|    49|        Credentials(
    50|    50|            clientEmail = json.getString("client_email"),
    51|    51|            privateKeyPem = json.getString("private_key"),
    52|    52|            tokenUri = json.getString("token_uri")
    53|    53|        )
    54|    54|    }
    55|    55|
    56|    56|    suspend fun getAccessToken(): String = mutex.withLock {
    57|    57|        val now = System.currentTimeMillis()
    58|    58|        if (cachedToken != null && now < tokenExpiresAt - 60_000) {
    59|    59|            return cachedToken!!
    60|    60|        }
    61|    61|        return withContext(Dispatchers.IO) {
    62|    62|            val jwt = createSignedJwt()
    63|    63|            val (token, expiresIn) = exchangeJwtForToken(jwt)
    64|    64|            cachedToken = token
    65|    65|            tokenExpiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
    66|    66|            token
    67|    67|        }
    68|    68|    }
    69|    69|
    70|    70|    private fun createSignedJwt(): String {
    71|    71|        val now = System.currentTimeMillis() / 1000
    72|    72|        val exp = now + 3600
    73|    73|
    74|    74|        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
    75|    75|
    76|    76|        val claims = JSONObject().apply {
    77|    77|            put("iss", credentials.clientEmail)
    78|    78|            put("scope", "https://www.googleapis.com/auth/drive.readonly")
    79|    79|            put("aud", credentials.tokenUri)
    80|    80|            put("iat", now)
    81|    81|            put("exp", exp)
    82|    82|        }
    83|    83|        val payload = base64Url(claims.toString().toByteArray())
    84|    84|
    85|    85|        val signInput = "$header.$payload"
    86|    86|        val signature = signRsa256(signInput.toByteArray(), credentials.privateKeyPem)
    87|    87|
    88|    88|        return "$signInput.${base64Url(signature)}"
    89|    89|    }
    90|    90|
    91|    91|    private fun exchangeJwtForToken(jwt: String): Pair<String, Long> {
    92|    92|        val body = FormBody.Builder()
    93|    93|            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
    94|    94|            .add("assertion", jwt)
    95|    95|            .build()
    96|    96|
    97|    97|        val request = Request.Builder()
    98|    98|            .url(credentials.tokenUri)
    99|    99|            .post(body)
   100|   100|            .build()
   101|   101|
   102|   102|        val response = client.newCall(request).execute()
   103|   103|        response.use { resp ->
   104|   104|            if (!resp.isSuccessful) {
   105|   105|                throw Exception("Token exchange failed: HTTP ${resp.code} ${resp.message}")
   106|   106|            }
   107|   107|            val respBody = resp.body ?: throw Exception("Token exchange returned empty body")
   108|   108|            val responseBody = respBody.string()
   109|   109|            val json = JSONObject(responseBody)
   110|   110|
   111|   111|            if (json.has("error")) {
   112|   112|                throw Exception("Token exchange failed: ${json.optString("error_description", responseBody)}")
   113|   113|            }
   114|   114|
   115|   115|            val token = json.getString("access_token")
   116|   116|            val expiresIn = json.optLong("expires_in", 3600L)
   117|   117|            return token to expiresIn
   118|   118|        }
   119|   119|    }
   120|   120|
   121|   121|    /**
   122|   122|     * Signs data using RS256 with a PEM-encoded private key.
   123|   123|     * Handles both real Google service account keys (with -----BEGIN PRIVATE KEY-----
   124|   124|     * headers) and placeholder/redacted keys gracefully by stripping non-Base64
   125|   125|     * content before decoding.
   126|   126|     */
   127|   127|    private fun signRsa256(data: ByteArray, pemKey: String): ByteArray {
   128|   128|        // Strip PEM armor (real keys) and placeholder markers, keeping only raw Base64
   129|   129|        val stripped = pemKey
   130|   130|            .replace("-----BEGIN PRIVATE KEY-----", "")
   131|   131|            .replace("-----END PRIVATE KEY-----", "")
   132|   132|            .replace("[REDACTED PRIVATE KEY]", "")
   133|   133|            .replace("\\n", "")
   134|   134|            .replace("\n", "")
   135|   135|            .trim()
   136|   136|
   137|   137|        val keyBytes = Base64.decode(stripped, Base64.DEFAULT)
   138|   138|        val keySpec = PKCS8EncodedKeySpec(keyBytes)
   139|   139|        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)
   140|   140|
   141|   141|        return Signature.getInstance("SHA256withRSA").run {
   142|   142|            initSign(privateKey)
   143|   143|            update(data)
   144|   144|            sign()
   145|   145|        }
   146|   146|    }
   147|   147|
   148|   148|    private fun base64Url(data: ByteArray): String {
   149|   149|        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
   150|   150|    }
   151|   151|}
   152|   152|