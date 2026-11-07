package com.postoffice.app.data

import com.postoffice.app.model.KeyValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun kvToJson(list: List<KeyValue>) = JSONArray().apply {
    list.filter { it.key.isNotBlank() || it.value.isNotBlank() }.forEach {
        put(JSONObject().put("k", it.key).put("v", it.value).put("e", it.enabled).put("s", it.secret))
    }
}

/** Parses rows and appends the trailing blank row the editors expect. */
internal fun kvFromJson(arr: JSONArray?): List<KeyValue> {
    val rows = if (arr == null) emptyList() else (0 until arr.length()).map {
        val o = arr.getJSONObject(it)
        KeyValue(o.optString("k"), o.optString("v"), o.optBoolean("e", true), o.optBoolean("s", false))
    }
    return rows + KeyValue()
}

internal inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

/** Write via a temp file so a crash mid-write can't leave a half-written file. */
internal fun File.writeAtomically(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        delete()
        tmp.renameTo(this)
    }
}
