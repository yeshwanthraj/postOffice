package com.postoffice.app.script

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptRunnerTest {
    private val runner = ScriptRunner(timeoutMillis = 500)

    private val request = ScriptRequest(
        method = "GET", url = "{{baseUrl}}/users",
        params = listOf("page" to "1"), headers = listOf("Accept" to "application/json"),
        bodyMode = "none", rawBody = "", urlencoded = emptyList(),
    )
    private val response = ScriptResponse(
        code = 200, status = "OK", headers = listOf("Content-Type" to "application/json"),
        body = """{"token":"abc","items":[1,2,3],"user":{"id":7}}""", responseTimeMs = 412, sizeBytes = 48,
    )

    private fun pre(script: String, env: String? = "Staging") = runBlocking {
        runner.run(script, ScriptInput(ScriptPhase.PRE, request, null, env, mapOf("baseUrl" to "https://s.io"), mapOf("g" to "1")))
    }

    private fun post(script: String) = runBlocking {
        runner.run(script, ScriptInput(ScriptPhase.POST, request, response, "Staging", mapOf("baseUrl" to "https://s.io")))
    }

    @Test fun preRequestEditsRequestAndRecordsVariableOps() {
        val out = pre(
            """
            pm.environment.set("ts", 123);
            pm.globals.set("g2", "x");
            pm.environment.unset("old");
            pm.variables.set("local", "L");
            pm.request.headers.add("X-Sig", pm.crypto.sha256("a"));
            pm.request.headers.upsert({ key: "accept", value: "text/plain" });
            pm.request.params.add("q", pm.variables.get("baseUrl"));
            pm.request.method = "post";
            pm.request.body.mode = "json";
            pm.request.body.raw = JSON.stringify({ a: 1 });
            console.log("env is", pm.environment.name, { n: 1 });
            """.trimIndent()
        )
        assertNull(out.error)
        assertEquals(
            listOf(
                VarOp.Set(VarScopeName.ENVIRONMENT, "ts", "123"),
                VarOp.Set(VarScopeName.GLOBALS, "g2", "x"),
                VarOp.Unset(VarScopeName.ENVIRONMENT, "old"),
            ),
            out.ops,
        )
        assertEquals("L", out.locals["local"])
        val r = out.request!!
        assertEquals("post", r.method)
        assertEquals(
            listOf("Accept" to "text/plain", "X-Sig" to "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb"),
            r.headers,
        )
        assertEquals(listOf("page" to "1", "q" to "https://s.io"), r.params)
        assertEquals("json", r.bodyMode)
        assertEquals("""{"a":1}""", r.rawBody)
        assertEquals(LogLine(ScriptPhase.PRE, LogLevel.LOG, """env is Staging {"n":1}"""), out.logs.single())
    }

    @Test fun environmentWritesWithoutActiveEnvironmentAreIgnoredWithWarning() {
        val out = pre("""pm.environment.set("a", "1"); pm.globals.set("b", "2");""", env = null)
        assertNull(out.error)
        assertEquals(listOf(VarOp.Set(VarScopeName.GLOBALS, "b", "2")), out.ops)
        assertEquals(LogLevel.WARN, out.logs.single().level)
    }

    @Test fun postResponseTestsPassAndFailWithMessages() {
        val out = post(
            """
            pm.test("Status is 200", () => pm.response.to.have.status(200));
            pm.test("Is ok", () => { pm.response.to.be.ok; });
            pm.test("Has token", () => {
                const body = pm.response.json();
                pm.expect(body).to.have.property("token").that.is.a("string");
                pm.expect(body.items).to.have.lengthOf(3).and.include(2);
                pm.expect(body.user).to.eql({ id: 7 });
                pm.environment.set("token", body.token);
            });
            pm.test("Fast", () => pm.expect(pm.response.responseTime).to.be.below(200));
            pm.test("Header", () => pm.expect(pm.response.headers.get("content-type")).to.include("json"));
            pm.test("Not 404", () => pm.expect(pm.response.code).to.not.equal(404));
            pm.test("Throws", () => { undefinedThing.x; });
            pm.test.skip("Later");
            """.trimIndent()
        )
        assertNull(out.error)
        val byName = out.tests.associateBy { it.name }
        listOf("Status is 200", "Is ok", "Has token", "Header", "Not 404").forEach {
            assertEquals(it + ": " + byName.getValue(it).error, TestStatus.PASS, byName.getValue(it).status)
        }
        assertEquals("expected 412 to be below 200", byName.getValue("Fast").error)
        assertEquals(TestStatus.FAIL, byName.getValue("Throws").status)
        assertTrue(byName.getValue("Throws").error!!.contains("undefinedThing"))
        assertEquals(TestStatus.SKIP, byName.getValue("Later").status)
        assertEquals(listOf(VarOp.Set(VarScopeName.ENVIRONMENT, "token", "abc")), out.ops)
        assertNull(out.request)
    }

    @Test fun failedScriptKeepsLogsAndEarlierTestsButDropsChanges() {
        val out = post(
            """
            console.log("before");
            pm.environment.set("x", "1");
            pm.test("first", () => {});
            null.boom;
            """.trimIndent()
        )
        assertNotNull(out.error)
        assertTrue(out.ops.isEmpty())
        assertEquals("before", out.logs.single().message)
        assertEquals(TestStatus.PASS, out.tests.single().status)
    }

    @Test fun infiniteLoopIsStoppedByTimeLimit() {
        val started = System.currentTimeMillis()
        val out = pre("while (true) {}")
        out.error!!.let { e -> assertTrue(e, e.contains("ran longer")) }
        assertTrue(System.currentTimeMillis() - started < 3_000)
    }

    @Test fun syntaxErrorIsReported() {
        val out = pre("let = ;")
        out.error!!.let { e -> assertTrue(e, e.contains("SyntaxError")) }
    }

    @Test fun cryptoHelpers() {
        val out = pre(
            """
            pm.environment.set("h", pm.crypto.hmacSha256("key", "The quick brown fox jumps over the lazy dog"));
            pm.environment.set("b", btoa("user:pass"));
            pm.environment.set("d", atob("dXNlcjpwYXNz"));
            pm.environment.set("u", pm.uuid().length);
            """.trimIndent()
        )
        assertNull(out.error)
        val values = out.ops.filterIsInstance<VarOp.Set>().associate { it.key to it.value }
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8", values["h"])
        assertEquals("dXNlcjpwYXNz", values["b"])
        assertEquals("user:pass", values["d"])
        assertEquals("36", values["u"])
    }
}
