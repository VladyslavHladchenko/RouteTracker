package com.example.routetracker.data

import android.util.Log
import com.example.routetracker.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

private const val API_TAG = "GolemioApi"
private const val API_BASE_URL = "https://api.golemio.cz"
private const val DEFAULT_TIMEOUT_MILLIS = 10_000
private const val MAX_LOG_BODY_LENGTH = 500

data class ApiObjectResponse(
    val body: JSONObject,
    val cacheControl: String?,
)

data class ApiArrayResponse(
    val body: JSONArray,
    val cacheControl: String?,
)

class GolemioApiClient {
    fun getObject(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        retryOnRateLimit: Boolean = true,
    ): ApiObjectResponse {
        val response = execute(path, params, retryOnRateLimit)
        return ApiObjectResponse(
            body = JSONObject(response.body),
            cacheControl = response.cacheControl,
        )
    }

    fun getArray(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        retryOnRateLimit: Boolean = true,
    ): ApiArrayResponse {
        val response = execute(path, params, retryOnRateLimit)
        return ApiArrayResponse(
            body = JSONArray(response.body),
            cacheControl = response.cacheControl,
        )
    }

    private fun execute(
        path: String,
        params: List<Pair<String, String>>,
        retryOnRateLimit: Boolean,
    ): RawApiResponse {
        val apiToken = requireApiToken()
        val query = params.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
        val url = URL(
            buildString {
                append(API_BASE_URL)
                append(path)
                if (query.isNotEmpty()) {
                    append('?')
                    append(query)
                }
            }
        )

        repeat(4) { attempt ->
            Log.d(API_TAG, "GET $path attempt=${attempt + 1} params=$params")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DEFAULT_TIMEOUT_MILLIS
                readTimeout = DEFAULT_TIMEOUT_MILLIS
                setRequestProperty("accept", "application/json")
                setRequestProperty("X-Access-Token", apiToken)
            }

            try {
                val statusCode = connection.responseCode
                val responseBody = readResponseBody(connection, statusCode)
                val cacheControl = connection.getHeaderField("Cache-Control")

                if (statusCode == 429) {
                    val retryAfterSeconds = connection.getHeaderField("Retry-After")
                        ?.toDoubleOrNull()
                        ?: (1.5 * (attempt + 1))
                    if (!retryOnRateLimit) {
                        Log.w(API_TAG, "Rate limited on $path. Skipping retries for this request.")
                        throw ApiException(
                            statusCode = statusCode,
                            message = "HTTP $statusCode for $path",
                            responseBody = responseBody,
                        )
                    }
                    Log.w(API_TAG, "Rate limited on $path. retryAfterSeconds=$retryAfterSeconds")
                    Thread.sleep(min((retryAfterSeconds * 1_000).toLong(), 6_000L))
                    return@repeat
                }

                if (statusCode !in 200..299) {
                    if (statusCode == 404 && path.startsWith("/v2/vehiclepositions/")) {
                        Log.d(
                            API_TAG,
                            "Request returned no live vehicle position. path=$path status=$statusCode body=${responseBody.truncatedForLog()}",
                        )
                    } else {
                        Log.e(
                            API_TAG,
                            "Request failed. path=$path status=$statusCode body=${responseBody.truncatedForLog()}",
                        )
                    }
                    throw ApiException(
                        statusCode = statusCode,
                        message = "HTTP $statusCode for $path",
                        responseBody = responseBody,
                    )
                }

                Log.d(API_TAG, "Request succeeded. path=$path status=$statusCode")
                return RawApiResponse(
                    body = responseBody,
                    cacheControl = cacheControl,
                )
            } finally {
                connection.disconnect()
            }
        }

        throw IOException("Golemio API rate-limited the request too many times.")
    }

    private fun readResponseBody(
        connection: HttpURLConnection,
        statusCode: Int,
    ): String {
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }
}

private fun requireApiToken(): String {
    val token = BuildConfig.GOLEMIO_API_KEY.trim()
    check(token.isNotEmpty()) {
        "Missing Golemio API key. Set golemioApiKey in ~/.gradle/gradle.properties or GOLEMIO_API_KEY in the environment."
    }
    return token
}

internal class ApiException(
    val statusCode: Int,
    val responseBody: String,
    message: String,
) : IOException(message)

private data class RawApiResponse(
    val body: String,
    val cacheControl: String?,
)

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)

private fun String.truncatedForLog(): String {
    return if (length <= MAX_LOG_BODY_LENGTH) this else take(MAX_LOG_BODY_LENGTH) + "..."
}
