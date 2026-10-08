package app.androcleaner.feature.protection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject

sealed interface LookupResult<out T> {
    data class Found<T>(val value: T) : LookupResult<T>
    data object NotFound : LookupResult<Nothing>
    data object InvalidKey : LookupResult<Nothing>
    data object RateLimited : LookupResult<Nothing>
    data class Failed(val message: String) : LookupResult<Nothing>
}

data class VirusTotalStats(val malicious: Int, val suspicious: Int, val total: Int)

/** Only the SHA-256 of an APK is sent — never the file itself. */
class VirusTotalClient @Inject constructor() {
    suspend fun lookup(sha256: String, apiKey: String): LookupResult<VirusTotalStats> = withContext(Dispatchers.IO) {
        request("https://www.virustotal.com/api/v3/files/$sha256", mapOf("x-apikey" to apiKey)) { code, body ->
            when (code) {
                200 -> {
                    val stats = JSONObject(body).getJSONObject("data").getJSONObject("attributes")
                        .getJSONObject("last_analysis_stats")
                    val total = stats.keys().asSequence().sumOf { stats.optInt(it) }
                    LookupResult.Found(VirusTotalStats(stats.optInt("malicious"), stats.optInt("suspicious"), total))
                }
                404 -> LookupResult.NotFound
                401, 403 -> LookupResult.InvalidKey
                429 -> LookupResult.RateLimited
                else -> LookupResult.Failed("HTTP $code")
            }
        }
    }
}

/** abuse.ch MalwareBazaar: known Android malware samples by hash. */
class MalwareBazaarClient @Inject constructor() {
    suspend fun lookup(sha256: String, authKey: String): LookupResult<String> = withContext(Dispatchers.IO) {
        val form = "query=get_info&hash=" + URLEncoder.encode(sha256, "UTF-8")
        request("https://mb-api.abuse.ch/api/v1/", mapOf("Auth-Key" to authKey), form) { code, body ->
            if (code == 401 || code == 403) return@request LookupResult.InvalidKey
            if (code == 429) return@request LookupResult.RateLimited
            if (code != 200) return@request LookupResult.Failed("HTTP $code")
            val json = JSONObject(body)
            when (val status = json.optString("query_status")) {
                "ok" -> {
                    val sample = json.getJSONArray("data").getJSONObject(0)
                    LookupResult.Found(sample.optString("signature").ifBlank { sample.optString("file_type", "malware") })
                }
                "hash_not_found", "no_results" -> LookupResult.NotFound
                "unknown_auth_key", "wrong_auth_key" -> LookupResult.InvalidKey
                else -> LookupResult.Failed(status)
            }
        }
    }
}

private fun <T> request(
    url: String,
    headers: Map<String, String>,
    formBody: String? = null,
    parse: (code: Int, body: String) -> LookupResult<T>,
): LookupResult<T> = try {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        headers.forEach { (k, v) -> setRequestProperty(k, v) }
        if (formBody != null) {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            outputStream.use { it.write(formBody.toByteArray()) }
        }
    }
    val code = connection.responseCode
    val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
        ?.bufferedReader()?.use { it.readText() }.orEmpty()
    connection.disconnect()
    parse(code, body)
} catch (e: Exception) {
    LookupResult.Failed(e.message ?: e.javaClass.simpleName)
}
