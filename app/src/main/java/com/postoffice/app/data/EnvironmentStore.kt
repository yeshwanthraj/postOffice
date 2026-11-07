package com.postoffice.app.data

import com.postoffice.app.model.Environment
import com.postoffice.app.model.EnvironmentState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists globals, environments and the active selection in app-private storage
 * (filesDir/environments.json). Secret values are masked in the UI only; they are
 * stored as plain text like everything else in app-private storage.
 */
class EnvironmentStore(private val file: File) {

    fun load(): EnvironmentState = try {
        if (!file.exists()) EnvironmentState() else fromJson(JSONObject(file.readText()))
    } catch (_: Exception) {
        EnvironmentState()
    }

    fun save(state: EnvironmentState) = file.writeAtomically(toJson(state).toString())

    private fun toJson(s: EnvironmentState) = JSONObject().apply {
        put("activeId", s.activeId ?: JSONObject.NULL)
        put("globals", kvToJson(s.globals))
        put("environments", JSONArray().apply {
            s.environments.forEach { e ->
                put(JSONObject().put("id", e.id).put("name", e.name).put("variables", kvToJson(e.variables)))
            }
        })
    }

    private fun fromJson(o: JSONObject): EnvironmentState {
        val arr = o.optJSONArray("environments") ?: JSONArray()
        val envs = (0 until arr.length()).map {
            val e = arr.getJSONObject(it)
            Environment(id = e.getString("id"), name = e.optString("name", "Untitled"), variables = kvFromJson(e.optJSONArray("variables")))
        }
        val active = if (o.isNull("activeId")) null else o.optString("activeId").takeIf { id -> envs.any { it.id == id } }
        return EnvironmentState(globals = kvFromJson(o.optJSONArray("globals")), environments = envs, activeId = active)
    }
}
