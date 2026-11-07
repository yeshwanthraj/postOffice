package com.postoffice.app.model

import java.util.UUID

data class Environment(
    val name: String,
    val variables: List<KeyValue> = listOf(KeyValue()),
    val id: String = UUID.randomUUID().toString(),
)

/** Globals plus named environments, one of which may be active. */
data class EnvironmentState(
    val globals: List<KeyValue> = listOf(KeyValue()),
    val environments: List<Environment> = emptyList(),
    val activeId: String? = null,
) {
    val active: Environment? get() = environments.firstOrNull { it.id == activeId }

    fun scope(locals: Map<String, String> = emptyMap()) =
        VariableScope(locals, active?.variables.toVarMap(), globals.toVarMap())
}

fun List<KeyValue>?.toVarMap(): Map<String, String> =
    this.orEmpty().filter { it.isActive }.associate { it.key.trim() to it.value }

enum class VarSource { LOCAL, ENVIRONMENT, GLOBAL }

/**
 * Lookup order: script-local variables (pm.variables.set), then the active
 * environment, then globals.
 */
class VariableScope(
    val locals: Map<String, String> = emptyMap(),
    val environment: Map<String, String> = emptyMap(),
    val globals: Map<String, String> = emptyMap(),
) {
    fun lookup(name: String): String? = locals[name] ?: environment[name] ?: globals[name]

    fun source(name: String): VarSource? = when {
        name in locals -> VarSource.LOCAL
        name in environment -> VarSource.ENVIRONMENT
        name in globals -> VarSource.GLOBAL
        else -> null
    }
}

object Templates {
    /** Matches `{{name}}`, allowing whitespace inside the braces. */
    val PATTERN = Regex("""\{\{\s*([^{}]+?)\s*\}\}""")
    private const val MAX_DEPTH = 5

    /**
     * Replaces every `{{name}}` that [lookup] knows. Unknown names are left as written.
     * Values may themselves contain `{{other}}`; those are resolved up to [MAX_DEPTH] levels.
     */
    fun resolve(text: String, lookup: (String) -> String?): String {
        if (!text.contains("{{")) return text
        var current = text
        repeat(MAX_DEPTH) {
            val next = PATTERN.replace(current) { m -> lookup(m.groupValues[1]) ?: m.value }
            if (next == current) return next
            current = next
        }
        return current
    }

    fun names(text: String): Sequence<String> = PATTERN.findAll(text).map { it.groupValues[1] }
}

private fun List<KeyValue>.resolveRows(scope: VariableScope) = map {
    if (!it.isActive) it
    else it.copy(key = Templates.resolve(it.key, scope::lookup), value = Templates.resolve(it.value, scope::lookup))
}

/** Substitutes variables in every field that ends up on the wire. */
fun ApiRequest.resolve(scope: VariableScope): ApiRequest {
    val r: (String) -> String = { Templates.resolve(it, scope::lookup) }
    return copy(
        url = r(url),
        params = params.resolveRows(scope),
        headers = headers.resolveRows(scope),
        rawBody = r(rawBody),
        formBody = formBody.resolveRows(scope),
        auth = auth.copy(
            bearerToken = r(auth.bearerToken),
            username = r(auth.username),
            password = r(auth.password),
            apiKeyName = r(auth.apiKeyName),
            apiKeyValue = r(auth.apiKeyValue),
        ),
    )
}

/** Variable names used by this request (in fields that will be sent) that [scope] can't resolve. */
fun ApiRequest.unresolvedNames(scope: VariableScope): Set<String> {
    val texts = buildList {
        add(url)
        (params + headers).filter { it.isActive }.forEach { add(it.key); add(it.value) }
        if (method.allowsBody) when (bodyType) {
            BodyType.NONE -> Unit
            BodyType.FORM_URLENCODED -> formBody.filter { it.isActive }.forEach { add(it.key); add(it.value) }
            else -> add(rawBody)
        }
        when (auth.type) {
            AuthType.NONE -> Unit
            AuthType.BEARER -> add(auth.bearerToken)
            AuthType.BASIC -> { add(auth.username); add(auth.password) }
            AuthType.API_KEY -> { add(auth.apiKeyName); add(auth.apiKeyValue) }
        }
    }
    return texts.flatMap { t -> Templates.names(Templates.resolve(t, scope::lookup)).toList() }.toSet()
}
