package com.postoffice.app.net

import com.postoffice.app.model.ApiKeyLocation
import com.postoffice.app.model.ApiRequest
import com.postoffice.app.model.ApiResponse
import com.postoffice.app.model.AuthType
import com.postoffice.app.model.BodyType
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RequestBuildException(message: String) : Exception(message)

/** Turns an [ApiRequest] into an OkHttp call and executes it. */
class HttpEngine(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {

    fun build(req: ApiRequest): Request {
        val rawUrl = req.url.trim().let {
            when {
                it.isEmpty() -> throw RequestBuildException("Enter a URL")
                it.startsWith("http://", true) || it.startsWith("https://", true) -> it
                else -> "https://$it"
            }
        }
        val baseUrl = rawUrl.toHttpUrlOrNull()
            ?: throw RequestBuildException("Invalid URL: $rawUrl")

        val urlBuilder = baseUrl.newBuilder()
        req.params.filter { it.isActive }.forEach { urlBuilder.addQueryParameter(it.key.trim(), it.value) }

        val builder = Request.Builder()
        req.headers.filter { it.isActive }.forEach { builder.addHeader(it.key.trim(), it.value) }

        val auth = req.auth
        when (auth.type) {
            AuthType.NONE -> Unit
            AuthType.BEARER -> builder.header("Authorization", "Bearer ${auth.bearerToken.trim()}")
            AuthType.BASIC -> builder.header("Authorization", Credentials.basic(auth.username, auth.password))
            AuthType.API_KEY -> {
                if (auth.apiKeyName.isBlank()) throw RequestBuildException("API key name is empty")
                when (auth.apiKeyLocation) {
                    ApiKeyLocation.HEADER -> builder.header(auth.apiKeyName.trim(), auth.apiKeyValue)
                    ApiKeyLocation.QUERY -> urlBuilder.addQueryParameter(auth.apiKeyName.trim(), auth.apiKeyValue)
                }
            }
        }

        builder.url(urlBuilder.build())
        val userContentType = req.headers.firstOrNull {
            it.isActive && it.key.trim().equals("Content-Type", ignoreCase = true)
        }?.value
        builder.method(req.method.name, buildBody(req, userContentType))
        return builder.build()
    }

    private fun buildBody(req: ApiRequest, userContentType: String?): RequestBody? {
        if (!req.method.allowsBody) return null
        val type = req.bodyType
        val body: RequestBody = when (type) {
            BodyType.NONE -> ByteArray(0).toRequestBody(null)
            BodyType.FORM_URLENCODED -> {
                val form = FormBody.Builder().apply {
                    req.formBody.filter { it.isActive }.forEach { add(it.key.trim(), it.value) }
                }.build()
                if (userContentType == null) {
                    form
                } else {
                    // Respect a user-supplied Content-Type while still url-encoding the fields.
                    val buffer = okio.Buffer().also { form.writeTo(it) }
                    buffer.readByteArray().toRequestBody(userContentType.toMediaTypeOrNull())
                }
            }
            // Encode as bytes so OkHttp sends the Content-Type exactly as given (no charset appended).
            else -> req.rawBody.toByteArray(Charsets.UTF_8)
                .toRequestBody((userContentType ?: type.defaultContentType)?.toMediaTypeOrNull())
        }
        // DELETE/OPTIONS with an empty "None" body: send no body at all.
        if (type == BodyType.NONE && req.method.name in setOf("DELETE", "OPTIONS")) return null
        return body
    }

    suspend fun execute(req: ApiRequest): ApiResponse {
        val request = build(req)
        val started = System.nanoTime()
        val call = client.newCall(request)
        val response = call.await()
        return response.use { resp ->
            val source = resp.body
            val bytes = source?.let { readCapped(it.byteStream()) } ?: Captured(ByteArray(0), 0, false)
            val elapsed = (System.nanoTime() - started) / 1_000_000
            val contentType = resp.header("Content-Type")
            val charset = source?.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            val text = String(bytes.data, charset)
            ApiResponse(
                code = resp.code,
                message = resp.message,
                headers = resp.headers.map { it.first to it.second },
                body = prettyPrint(text, contentType),
                contentType = contentType,
                durationMs = elapsed,
                sizeBytes = bytes.total,
                finalUrl = resp.request.url.toString(),
                truncated = bytes.truncated,
            )
        }
    }

    private class Captured(val data: ByteArray, val total: Long, val truncated: Boolean)

    private fun readCapped(stream: java.io.InputStream): Captured {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0L
        stream.use {
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                if (total < MAX_DISPLAY_BYTES) {
                    out.write(buf, 0, minOf(n.toLong(), MAX_DISPLAY_BYTES - total).toInt())
                }
                total += n
            }
        }
        return Captured(out.toByteArray(), total, total > MAX_DISPLAY_BYTES)
    }

    companion object {
        const val MAX_DISPLAY_BYTES = 2L * 1024 * 1024

        fun prettyPrint(text: String, contentType: String?): String {
            val trimmed = text.trim()
            val looksJson = contentType?.contains("json", true) == true ||
                trimmed.startsWith("{") || trimmed.startsWith("[")
            if (!looksJson || trimmed.isEmpty()) return text
            return formatJson(trimmed) ?: text
        }

        /** Returns indented JSON or null when [text] isn't valid JSON. */
        fun formatJson(text: String): String? = try {
            val t = text.trim()
            val out = if (t.startsWith("[")) JSONArray(t).toString(2) else JSONObject(t).toString(2)
            out.replace("\\/", "/")
        } catch (_: Exception) {
            null
        }
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, value, _ -> value.close() }
        }
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
