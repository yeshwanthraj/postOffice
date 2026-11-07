package com.postoffice.app.script

import com.postoffice.app.model.ApiRequest
import com.postoffice.app.model.ApiResponse
import com.postoffice.app.model.BodyType
import com.postoffice.app.model.HttpMethod
import com.postoffice.app.model.KeyValue

enum class ScriptPhase(val label: String, val fileName: String) {
    PRE("pre", "pre-request.js"),
    POST("post", "post-response.js"),
}

/** The request as scripts see it (`pm.request`). Only enabled rows are included. */
data class ScriptRequest(
    val method: String,
    val url: String,
    val params: List<Pair<String, String>>,
    val headers: List<Pair<String, String>>,
    val bodyMode: String,
    val rawBody: String,
    val urlencoded: List<Pair<String, String>>,
)

data class ScriptResponse(
    val code: Int,
    val status: String,
    val headers: List<Pair<String, String>>,
    val body: String,
    val responseTimeMs: Long,
    val sizeBytes: Long,
)

data class ScriptInput(
    val phase: ScriptPhase,
    val request: ScriptRequest,
    val response: ScriptResponse? = null,
    val environmentName: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val globals: Map<String, String> = emptyMap(),
    val locals: Map<String, String> = emptyMap(),
)

enum class VarScopeName { ENVIRONMENT, GLOBALS }

/** A pm.environment / pm.globals change, applied in order once the script finishes. */
sealed interface VarOp {
    val scope: VarScopeName
    val key: String

    data class Set(override val scope: VarScopeName, override val key: String, val value: String) : VarOp
    data class Unset(override val scope: VarScopeName, override val key: String) : VarOp
}

enum class TestStatus { PASS, FAIL, SKIP }

data class TestResult(val name: String, val status: TestStatus, val error: String? = null)

enum class LogLevel { LOG, INFO, WARN, ERROR }

data class LogLine(val phase: ScriptPhase, val level: LogLevel, val message: String)

data class ScriptOutput(
    /** Script-modified request (pre-request only, null when the script failed). */
    val request: ScriptRequest?,
    val ops: List<VarOp>,
    val locals: Map<String, String>,
    val tests: List<TestResult>,
    val logs: List<LogLine>,
    /** Non-null when the script threw or timed out. Changes must not be applied then. */
    val error: String?,
)

// ---- Mapping between app models and script models ----

private fun List<KeyValue>.activePairs() = filter { it.isActive }.map { it.key.trim() to it.value }

private fun BodyType.scriptMode() = when (this) {
    BodyType.NONE -> "none"
    BodyType.JSON -> "json"
    BodyType.TEXT -> "text"
    BodyType.XML -> "xml"
    BodyType.FORM_URLENCODED -> "urlencoded"
}

private fun bodyTypeFromMode(mode: String, fallback: BodyType) = when (mode.lowercase()) {
    "none" -> BodyType.NONE
    "json" -> BodyType.JSON
    "text", "raw" -> BodyType.TEXT
    "xml" -> BodyType.XML
    "urlencoded" -> BodyType.FORM_URLENCODED
    else -> fallback
}

fun ApiRequest.toScriptRequest() = ScriptRequest(
    method = method.name,
    url = url,
    params = params.activePairs(),
    headers = headers.activePairs(),
    bodyMode = bodyType.scriptMode(),
    rawBody = rawBody,
    urlencoded = formBody.activePairs(),
)

/** Applies a pre-request script's changes to the request that is about to be sent. */
fun ApiRequest.withScriptRequest(sr: ScriptRequest): ApiRequest {
    fun rows(pairs: List<Pair<String, String>>) = pairs.map { (k, v) -> KeyValue(k, v) }
    return copy(
        method = HttpMethod.entries.firstOrNull { it.name == sr.method.trim().uppercase() } ?: method,
        url = sr.url,
        params = rows(sr.params),
        headers = rows(sr.headers),
        bodyType = bodyTypeFromMode(sr.bodyMode, bodyType),
        rawBody = sr.rawBody,
        formBody = rows(sr.urlencoded),
    )
}

fun ApiResponse.toScriptResponse() = ScriptResponse(
    code = code,
    status = message,
    headers = headers,
    body = body,
    responseTimeMs = durationMs,
    sizeBytes = sizeBytes,
)

/** Applies pm.environment / pm.globals operations to an editable variable list. */
fun List<KeyValue>.applyOps(ops: List<VarOp>): List<KeyValue> {
    if (ops.isEmpty()) return this
    val rows = filter { it.key.isNotEmpty() || it.value.isNotEmpty() }.toMutableList()
    for (op in ops) when (op) {
        is VarOp.Set -> {
            val i = rows.indexOfFirst { it.key.trim() == op.key }
            if (i >= 0) rows[i] = rows[i].copy(value = op.value, enabled = true) else rows += KeyValue(op.key, op.value)
        }
        is VarOp.Unset -> rows.removeAll { it.key.trim() == op.key }
    }
    return rows + KeyValue()
}
