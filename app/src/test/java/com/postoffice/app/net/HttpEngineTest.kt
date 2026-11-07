package com.postoffice.app.net

import com.postoffice.app.model.ApiKeyLocation
import com.postoffice.app.model.ApiRequest
import com.postoffice.app.model.AuthConfig
import com.postoffice.app.model.AuthType
import com.postoffice.app.model.BodyType
import com.postoffice.app.model.HttpMethod
import com.postoffice.app.model.KeyValue
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpEngineTest {
    private val engine = HttpEngine()

    private fun bodyOf(r: okhttp3.Request) = Buffer().also { r.body!!.writeTo(it) }.readUtf8()

    @Test fun addsSchemeAndQueryParams() {
        val r = engine.build(ApiRequest(url = "example.com/a", params = listOf(KeyValue("q", "a b"), KeyValue("off", "1", enabled = false), KeyValue())))
        assertEquals("https://example.com/a?q=a%20b", r.url.toString())
        assertEquals("GET", r.method)
        assertNull(r.body)
    }

    @Test fun bearerAuth() {
        val r = engine.build(ApiRequest(url = "https://x.io", auth = AuthConfig(AuthType.BEARER, bearerToken = "abc")))
        assertEquals("Bearer abc", r.header("Authorization"))
    }

    @Test fun basicAuth() {
        val r = engine.build(ApiRequest(url = "https://x.io", auth = AuthConfig(AuthType.BASIC, username = "u", password = "p")))
        assertEquals("Basic dTpw", r.header("Authorization"))
    }

    @Test fun apiKeyInHeaderAndQuery() {
        val h = engine.build(ApiRequest(url = "https://x.io", auth = AuthConfig(AuthType.API_KEY, apiKeyName = "X-Key", apiKeyValue = "k1")))
        assertEquals("k1", h.header("X-Key"))
        val q = engine.build(ApiRequest(url = "https://x.io", auth = AuthConfig(AuthType.API_KEY, apiKeyName = "key", apiKeyValue = "k1", apiKeyLocation = ApiKeyLocation.QUERY)))
        assertEquals("k1", q.url.queryParameter("key"))
    }

    @Test fun jsonBodyWithDefaultContentType() {
        val r = engine.build(ApiRequest(method = HttpMethod.POST, url = "https://x.io", bodyType = BodyType.JSON, rawBody = "{\"a\":1}"))
        assertEquals("POST", r.method)
        assertEquals("application/json; charset=utf-8", r.body!!.contentType().toString())
        assertEquals("{\"a\":1}", bodyOf(r))
    }

    @Test fun userContentTypeOverrides() {
        val r = engine.build(ApiRequest(method = HttpMethod.PUT, url = "https://x.io", bodyType = BodyType.TEXT, rawBody = "hi",
            headers = listOf(KeyValue("content-type", "application/vnd.custom"))))
        assertEquals("application/vnd.custom", r.body!!.contentType().toString())
    }

    @Test fun formBody() {
        val r = engine.build(ApiRequest(method = HttpMethod.POST, url = "https://x.io", bodyType = BodyType.FORM_URLENCODED,
            formBody = listOf(KeyValue("a", "1 2"), KeyValue("b", "&"))))
        assertEquals("a=1%202&b=%26", bodyOf(r))
    }

    @Test fun postWithoutBodySendsEmptyBody_deleteSendsNone() {
        assertEquals(0L, engine.build(ApiRequest(method = HttpMethod.POST, url = "https://x.io")).body!!.contentLength())
        assertNull(engine.build(ApiRequest(method = HttpMethod.DELETE, url = "https://x.io")).body)
    }

    @Test(expected = RequestBuildException::class) fun rejectsBlankUrl() {
        engine.build(ApiRequest(url = "  "))
    }
}
