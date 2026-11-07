package com.postoffice.app.script

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Runs one pre-request or post-response script in a fresh QuickJS runtime.
 *
 * Data goes in once as JSON (`__input`), the bundled prelude builds `pm` from it,
 * the user's script runs, and everything comes back once via `__collect()`.
 * Only logging and crypto call into Kotlin while the script runs.
 */
class ScriptRunner(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    suspend fun run(script: String, input: ScriptInput): ScriptOutput = withContext(dispatcher) {
        val logs = Collections.synchronizedList(mutableListOf<LogLine>())
        val js = QuickJs.create(jobDispatcher = dispatcher)
        try {
            js.memoryLimit = MEMORY_LIMIT_BYTES
            js.evaluationTimeoutMillis = timeoutMillis
            js.function("__hostLog") { args ->
                val level = when (args.getOrNull(0)) {
                    "warn" -> LogLevel.WARN
                    "error" -> LogLevel.ERROR
                    "info" -> LogLevel.INFO
                    else -> LogLevel.LOG
                }
                logs += LogLine(input.phase, level, args.getOrNull(1)?.toString().orEmpty())
                null
            }
            js.function("__hostUuid") { UUID.randomUUID().toString() }
            js.function("__hostCrypto") { args ->
                hostCrypto(args[0].toString(), args.getOrNull(1)?.toString().orEmpty(), args.getOrNull(2)?.toString().orEmpty())
            }

            js.evaluate<Any?>("globalThis.__input = ${inputJson(input)};", "input.js")
            js.evaluate<Any?>(PRELUDE, "pm-prelude.js")

            val error = try {
                js.evaluate<Any?>(script, input.phase.fileName)
                null
            } catch (e: QuickJsInterruptedException) {
                "Script stopped: it ran longer than ${timeoutMillis / 1000.0} s"
            } catch (e: Exception) {
                e.message?.trim()?.ifEmpty { null } ?: e::class.simpleName ?: "Script failed"
            }

            // Tests that ran before an error are still worth showing.
            val collected = runCatching { JSONObject(js.evaluate<String>("__collect()", "collect.js")) }.getOrNull()
            val tests = collected?.optJSONArray("tests").toTests()
            if (error != null || collected == null) {
                ScriptOutput(null, emptyList(), input.locals, tests, logs.toList(), error ?: "Couldn't read the script's results")
            } else {
                ScriptOutput(
                    request = collected.optJSONObject("request")?.toScriptRequest(),
                    ops = collected.optJSONArray("ops").toOps(),
                    locals = collected.optJSONObject("locals").toStringMap(),
                    tests = tests,
                    logs = logs.toList(),
                    error = null,
                )
            }
        } catch (e: Exception) {
            // Failures in our own setup (input or prelude), not in the user's script.
            ScriptOutput(null, emptyList(), input.locals, emptyList(), logs.toList(), "Script engine error: ${e.message}")
        } finally {
            js.close()
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        private const val MEMORY_LIMIT_BYTES = 64L * 1024 * 1024

        private val PRELUDE: String by lazy {
            val stream = ScriptRunner::class.java.classLoader?.getResourceAsStream("pm-prelude.js")
                ?: error("pm-prelude.js is missing from the app resources")
            stream.bufferedReader().use { it.readText() }
        }

        // ---- JSON in ----

        private fun pairs(list: List<Pair<String, String>>) = JSONArray().apply {
            list.forEach { (k, v) -> put(JSONObject().put("key", k).put("value", v)) }
        }

        private fun map(m: Map<String, String>) = JSONObject().apply { m.forEach { (k, v) -> put(k, v) } }

        internal fun inputJson(input: ScriptInput): String {
            val r = input.request
            val o = JSONObject()
                .put("phase", input.phase.label)
                .put("environmentName", input.environmentName ?: JSONObject.NULL)
                .put("environment", map(input.environment))
                .put("globals", map(input.globals))
                .put("locals", map(input.locals))
                .put("request", JSONObject()
                    .put("method", r.method)
                    .put("url", r.url)
                    .put("params", pairs(r.params))
                    .put("headers", pairs(r.headers))
                    .put("body", JSONObject()
                        .put("mode", r.bodyMode)
                        .put("raw", r.rawBody)
                        .put("urlencoded", pairs(r.urlencoded))))
            input.response?.let { res ->
                o.put("response", JSONObject()
                    .put("code", res.code)
                    .put("status", res.status)
                    .put("headers", pairs(res.headers))
                    .put("body", res.body)
                    .put("responseTime", res.responseTimeMs)
                    .put("size", res.sizeBytes))
            }
            return o.toString()
        }

        // ---- JSON out ----

        private fun JSONArray?.toPairs(): List<Pair<String, String>> =
            if (this == null) emptyList()
            else (0 until length()).map { getJSONObject(it).let { o -> o.optString("key") to o.optString("value") } }

        private fun JSONObject?.toStringMap(): Map<String, String> =
            if (this == null) emptyMap() else keys().asSequence().associateWith { optString(it) }

        private fun JSONObject.toScriptRequest(): ScriptRequest {
            val body = optJSONObject("body") ?: JSONObject()
            return ScriptRequest(
                method = optString("method"),
                url = optString("url"),
                params = optJSONArray("params").toPairs(),
                headers = optJSONArray("headers").toPairs(),
                bodyMode = body.optString("mode", "none"),
                rawBody = body.optString("raw"),
                urlencoded = body.optJSONArray("urlencoded").toPairs(),
            )
        }

        private fun JSONArray?.toOps(): List<VarOp> = if (this == null) emptyList() else (0 until length()).mapNotNull {
            val o = getJSONObject(it)
            val scope = if (o.optString("scope") == "GLOBALS") VarScopeName.GLOBALS else VarScopeName.ENVIRONMENT
            when (o.optString("op")) {
                "set" -> VarOp.Set(scope, o.optString("key"), o.optString("value"))
                "unset" -> VarOp.Unset(scope, o.optString("key"))
                else -> null
            }
        }

        private fun JSONArray?.toTests(): List<TestResult> = if (this == null) emptyList() else (0 until length()).map {
            val o = getJSONObject(it)
            val status = runCatching { TestStatus.valueOf(o.optString("status")) }.getOrDefault(TestStatus.FAIL)
            TestResult(o.optString("name"), status, if (o.has("error")) o.optString("error") else null)
        }

        // ---- crypto helpers exposed as pm.crypto ----

        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

        private fun digest(alg: String, text: String) = hex(MessageDigest.getInstance(alg).digest(text.toByteArray()))

        private fun hmac(alg: String, key: String, text: String): String {
            val mac = Mac.getInstance(alg)
            mac.init(SecretKeySpec(key.toByteArray(), alg))
            return hex(mac.doFinal(text.toByteArray()))
        }

        internal fun hostCrypto(op: String, text: String, key: String): String = when (op) {
            "sha256" -> digest("SHA-256", text)
            "sha1" -> digest("SHA-1", text)
            "md5" -> digest("MD5", text)
            "hmacSha256" -> hmac("HmacSHA256", key, text)
            "hmacSha1" -> hmac("HmacSHA1", key, text)
            "base64Encode" -> Base64.getEncoder().encodeToString(text.toByteArray())
            "base64Decode" -> String(Base64.getDecoder().decode(text.trim()))
            else -> throw IllegalArgumentException("Unknown crypto operation $op")
        }
    }
}
