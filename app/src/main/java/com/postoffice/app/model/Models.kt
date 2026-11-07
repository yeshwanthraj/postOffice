package com.postoffice.app.model

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

enum class HttpMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS;

    /** Methods for which the Body tab is meaningful. */
    val allowsBody: Boolean get() = this != GET && this != HEAD
}

private val kvIds = AtomicLong(1)

/** A single editable key/value row (query param, header, form field). */
data class KeyValue(
    val key: String = "",
    val value: String = "",
    val enabled: Boolean = true,
    /** Only used for environment variables: mask the value in the UI. */
    val secret: Boolean = false,
    val id: Long = kvIds.getAndIncrement(),
) {
    val isActive: Boolean get() = enabled && key.isNotBlank()
}

enum class BodyType(val label: String, val defaultContentType: String?) {
    NONE("None", null),
    JSON("JSON", "application/json; charset=utf-8"),
    TEXT("Text", "text/plain; charset=utf-8"),
    XML("XML", "application/xml; charset=utf-8"),
    FORM_URLENCODED("Form URL-encoded", "application/x-www-form-urlencoded"),
}

enum class AuthType(val label: String) {
    NONE("No Auth"),
    BEARER("Bearer Token"),
    BASIC("Basic Auth"),
    API_KEY("API Key"),
}

enum class ApiKeyLocation(val label: String) { HEADER("Header"), QUERY("Query Param") }

data class AuthConfig(
    val type: AuthType = AuthType.NONE,
    val bearerToken: String = "",
    val username: String = "",
    val password: String = "",
    val apiKeyName: String = "X-API-Key",
    val apiKeyValue: String = "",
    val apiKeyLocation: ApiKeyLocation = ApiKeyLocation.HEADER,
)

data class ApiRequest(
    val id: String = UUID.randomUUID().toString(),
    val method: HttpMethod = HttpMethod.GET,
    val url: String = "",
    val params: List<KeyValue> = listOf(KeyValue()),
    val headers: List<KeyValue> = listOf(KeyValue()),
    val bodyType: BodyType = BodyType.NONE,
    val rawBody: String = "",
    val formBody: List<KeyValue> = listOf(KeyValue()),
    val auth: AuthConfig = AuthConfig(),
    val preRequestScript: String = "",
    val postResponseScript: String = "",
    val timestamp: Long = 0L,
)

data class ApiResponse(
    val code: Int,
    val message: String,
    val headers: List<Pair<String, String>>,
    val body: String,
    val contentType: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val finalUrl: String,
    val truncated: Boolean,
)

sealed interface ResponseState {
    data object Idle : ResponseState
    data object Loading : ResponseState
    data class Success(val response: ApiResponse) : ResponseState
    data class Error(val message: String, val durationMs: Long) : ResponseState
}
