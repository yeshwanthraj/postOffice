package com.postoffice.app.model

import com.postoffice.app.script.VarOp
import com.postoffice.app.script.VarScopeName
import com.postoffice.app.script.applyOps
import org.junit.Assert.assertEquals
import org.junit.Test

class EnvironmentsTest {
    private val staging = Environment(
        "Staging",
        listOf(KeyValue("baseUrl", "https://staging.x.io"), KeyValue("token", "abc"), KeyValue("off", "x", enabled = false), KeyValue()),
        id = "s",
    )
    private val state = EnvironmentState(
        globals = listOf(KeyValue("baseUrl", "https://x.io"), KeyValue("timeout", "30"), KeyValue("api", "{{baseUrl}}/v1"), KeyValue()),
        environments = listOf(staging),
        activeId = "s",
    )

    @Test fun environmentOverridesGlobalsAndLocalsOverrideBoth() {
        val scope = state.scope()
        assertEquals("https://staging.x.io", scope.lookup("baseUrl"))
        assertEquals("30", scope.lookup("timeout"))
        assertEquals(null, scope.lookup("off"))
        assertEquals("L", state.scope(mapOf("baseUrl" to "L")).lookup("baseUrl"))
        assertEquals("https://x.io", state.copy(activeId = null).scope().lookup("baseUrl"))
    }

    @Test fun resolvesNestedAndLeavesUnknownAsWritten() {
        val lookup = state.scope()::lookup
        assertEquals("https://staging.x.io/v1/users/{{id}}", Templates.resolve("{{ api }}/users/{{id}}", lookup))
    }

    @Test fun resolvesEveryRequestFieldAndReportsUnresolved() {
        val req = ApiRequest(
            method = HttpMethod.POST,
            url = "{{baseUrl}}/u/{{userId}}",
            headers = listOf(KeyValue("X-T", "{{timeout}}"), KeyValue("X-Off", "{{nope}}", enabled = false)),
            bodyType = BodyType.JSON,
            rawBody = """{"t":"{{token}}"}""",
            auth = AuthConfig(AuthType.BEARER, bearerToken = "{{token}}"),
        )
        val scope = state.scope()
        val resolved = req.resolve(scope)
        assertEquals("https://staging.x.io/u/{{userId}}", resolved.url)
        assertEquals("30", resolved.headers[0].value)
        assertEquals("{{nope}}", resolved.headers[1].value)
        assertEquals("""{"t":"abc"}""", resolved.rawBody)
        assertEquals("abc", resolved.auth.bearerToken)
        assertEquals(setOf("userId"), req.unresolvedNames(scope))
    }

    @Test fun applyOpsUpdatesAddsAndRemoves() {
        val out = staging.variables.applyOps(
            listOf(
                VarOp.Set(VarScopeName.ENVIRONMENT, "token", "new"),
                VarOp.Set(VarScopeName.ENVIRONMENT, "off", "on"),
                VarOp.Set(VarScopeName.ENVIRONMENT, "fresh", "1"),
                VarOp.Unset(VarScopeName.ENVIRONMENT, "baseUrl"),
            )
        )
        assertEquals(listOf("token" to "new", "off" to "on", "fresh" to "1", "" to ""), out.map { it.key to it.value })
        assertEquals(true, out[1].enabled)
    }
}
