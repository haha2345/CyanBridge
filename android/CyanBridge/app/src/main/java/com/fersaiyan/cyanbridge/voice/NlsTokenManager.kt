package com.fersaiyan.cyanbridge.voice

import android.util.Log
import com.fersaiyan.cyanbridge.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 阿里云 NLS Token 自动管理器。
 *
 * 使用 AccessKey ID + Secret 调用 CreateToken API 获取 Token，
 * 并在过期前自动刷新。
 *
 * API 文档参考: android/自动获取token.md
 *
 * 使用方式:
 * ```
 * val token = NlsTokenManager.getToken()
 * ```
 *
 * 需要在 local.properties 中配置:
 * ```
 * ALIYUN_AK_ID=LTAIxxxxxxxxxx
 * ALIYUN_AK_SECRET=xxxxxxxxxxxxxxxx
 * ```
 */
object NlsTokenManager {

    private const val TAG = "NlsToken"

    // Aliyun CreateToken API parameters
    private const val DOMAIN = "nls-meta.cn-shanghai.aliyuncs.com"
    private const val API_VERSION = "2019-02-28"
    private const val ACTION = "CreateToken"
    private const val REGION_ID = "cn-shanghai"

    // Refresh 5 minutes before expiry
    private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()

    @Volatile private var cachedToken: String? = null
    @Volatile private var expireTimeMs: Long = 0L

    /**
     * 获取有效的 Token。
     *
     * 优先返回缓存的 Token（未过期时），否则重新获取。
     * 如果 AK_ID / AK_SECRET 未配置，回退到 BuildConfig.ALIYUN_ASR_TOKEN。
     */
    suspend fun getToken(): String {
        val akId = BuildConfig.ALIYUN_AK_ID
        val akSecret = BuildConfig.ALIYUN_AK_SECRET

        // Fallback: if AK not configured, use static token from local.properties
        if (akId.isBlank() || akSecret.isBlank()) {
            val staticToken = BuildConfig.ALIYUN_ASR_TOKEN
            if (staticToken.isNotBlank()) {
                Log.w(TAG, "AK not configured, using static token (may expire)")
                return staticToken
            }
            throw IllegalStateException("No ALIYUN_AK_ID/AK_SECRET or ALIYUN_ASR_TOKEN configured")
        }

        // Return cached token if still valid
        val now = System.currentTimeMillis()
        val token = cachedToken
        if (token != null && now < expireTimeMs - REFRESH_MARGIN_MS) {
            return token
        }

        // Refresh token (thread-safe)
        return mutex.withLock {
            // Double-check after acquiring lock
            val nowInner = System.currentTimeMillis()
            val tokenInner = cachedToken
            if (tokenInner != null && nowInner < expireTimeMs - REFRESH_MARGIN_MS) {
                return@withLock tokenInner
            }

            Log.i(TAG, "Fetching new token from Aliyun...")
            val newToken = fetchToken(akId, akSecret)
            Log.i(TAG, "Token obtained, expires at ${Date(expireTimeMs)}")
            newToken
        }
    }

    /**
     * 同步获取 Token（用于非协程环境）。
     * 优先返回缓存，如果需要刷新则阻塞。
     */
    fun getTokenBlocking(): String {
        val akId = BuildConfig.ALIYUN_AK_ID
        val akSecret = BuildConfig.ALIYUN_AK_SECRET

        if (akId.isBlank() || akSecret.isBlank()) {
            val staticToken = BuildConfig.ALIYUN_ASR_TOKEN
            if (staticToken.isNotBlank()) return staticToken
            throw IllegalStateException("No AK or static token configured")
        }

        val now = System.currentTimeMillis()
        val token = cachedToken
        if (token != null && now < expireTimeMs - REFRESH_MARGIN_MS) {
            return token
        }

        // Synchronous fetch
        synchronized(this) {
            val nowInner = System.currentTimeMillis()
            val tokenInner = cachedToken
            if (tokenInner != null && nowInner < expireTimeMs - REFRESH_MARGIN_MS) {
                return tokenInner
            }
            return fetchToken(akId, akSecret)
        }
    }

    /**
     * Call Aliyun CreateToken API with HMAC-SHA1 signed request.
     *
     * Reference: https://help.aliyun.com/zh/isi/getting-started/use-http-or-https-to-obtain-an-access-token
     */
    private fun fetchToken(akId: String, akSecret: String): String {
        // Build common parameters
        val params = TreeMap<String, String>()
        params["Action"] = ACTION
        params["Version"] = API_VERSION
        params["Format"] = "JSON"
        params["AccessKeyId"] = akId
        params["SignatureMethod"] = "HMAC-SHA1"
        params["SignatureVersion"] = "1.0"
        params["SignatureNonce"] = UUID.randomUUID().toString()
        params["Timestamp"] = run {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(Date())
        }
        params["RegionId"] = REGION_ID

        // Build string to sign
        val sortedQuery = params.entries.joinToString("&") { (k, v) ->
            "${percentEncode(k)}=${percentEncode(v)}"
        }
        val stringToSign = "GET&${percentEncode("/")}&${percentEncode(sortedQuery)}"

        // HMAC-SHA1
        val signature = hmacSha1("${akSecret}&", stringToSign)
        params["Signature"] = signature

        // Build URL
        val urlBuilder = "https://$DOMAIN/".toHttpUrl().newBuilder()
        params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw RuntimeException("Empty response")

        Log.d(TAG, "CreateToken response: $body")

        if (response.code != 200) {
            throw RuntimeException("CreateToken failed: HTTP ${response.code}, body=$body")
        }

        val json = JSONObject(body)
        val tokenObj = json.optJSONObject("Token")
            ?: throw RuntimeException("No Token in response: $body")

        val token = tokenObj.getString("Id")
        val expireTimeSec = tokenObj.getLong("ExpireTime")

        cachedToken = token
        expireTimeMs = expireTimeSec * 1000L

        return token
    }

    private fun percentEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private fun hmacSha1(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val rawHmac = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(rawHmac)
    }
}
