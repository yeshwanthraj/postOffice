package com.postoffice.app.data

import com.postoffice.app.model.ApiKeyLocation
import com.postoffice.app.model.ApiRequest
import com.postoffice.app.model.AuthConfig
import com.postoffice.app.model.AuthType
import com.postoffice.app.model.BodyType
import com.postoffice.app.model.HttpMethod
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists sent requests as JSON in app-private storage (filesDir/history.json).
 * Note: auth values are stored too, so the history is as sensitive as the tokens you use.
 */
class HistoryStore(private val file: File) {

    fun load(): List<ApiRequest> = try {
        if (!file.exists()) emptyList()
        else JSONArray(file.readText()).let { arr -> (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) } }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(items: List<ApiRequest>) {
        val arr = JSONArray().apply { items.forEach { put(toJson(it)) } }
        file.writeAtomically(arr.toString())
    }

    private fun toJson(r: ApiRequest) = JSONObject().apply {
        put("id", r.id)
        put("method", r.method.name)
        put("url", r.url)
        put("params", kvToJson(r.params))
        put("headers", kvToJson(r.headers))
        put("bodyType", r.bodyType.name)
        put("rawBody", r.rawBody)
        put("formBody", kvToJson(r.formBody))
        put("timestamp", r.timestamp)
        put("preRequestScript", r.preRequestScript)
        put("postResponseScript", r.postResponseScript)
        put("auth", JSONObject().apply {
            put("type", r.auth.type.name)
            put("bearerToken", r.auth.bearerToken)
            put("username", r.auth.username)
            put("password", r.auth.password)
            put("apiKeyName", r.auth.apiKeyName)
            put("apiKeyValue", r.auth.apiKeyValue)
            put("apiKeyLocation", r.auth.apiKeyLocation.name)
        })
    }

    private fun fromJson(o: JSONObject): ApiRequest {
        val a = o.optJSONObject("auth") ?: JSONObject()
        return ApiRequest(
            id = o.getString("id"),
            method = enumOr(o.optString("method"), HttpMethod.GET),
            url = o.optString("url"),
            params = kvFromJson(o.optJSONArray("params")),
            headers = kvFromJson(o.optJSONArray("headers")),
            bodyType = enumOr(o.optString("bodyType"), BodyType.NONE),
            rawBody = o.optString("rawBody"),
            formBody = kvFromJson(o.optJSONArray("formBody")),
            timestamp = o.optLong("timestamp"),
            preRequestScript = o.optString("preRequestScript"),
            postResponseScript = o.optString("postResponseScript"),
            auth = AuthConfig(
                type = enumOr(a.optString("type"), AuthType.NONE),
                bearerToken = a.optString("bearerToken"),
                username = a.optString("username"),
                password = a.optString("password"),
                apiKeyName = a.optString("apiKeyName", "X-API-Key"),
                apiKeyValue = a.optString("apiKeyValue"),
                apiKeyLocation = enumOr(a.optString("apiKeyLocation"), ApiKeyLocation.HEADER),
            ),
        )
    }
}
