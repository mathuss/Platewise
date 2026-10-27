package com.countmymacros

import com.countmymacros.data.AiException
import com.countmymacros.data.FoodAi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Drives the real Anthropic SDK against a local stub server: checks the request we put on
 * the wire and how responses (normal, refusal, bad key) come back. No API key or credit used.
 */
class FoodAiTest {
    private lateinit var server: MockWebServer
    private lateinit var request: RecordedRequest
    private lateinit var body: JSONObject

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    /** Serves one canned response, runs [block] against it and records what was sent. */
    private fun <T> call(responseBody: String, status: Int = 200, block: (FoodAi) -> T): T {
        server.enqueue(
            MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(responseBody)
        )
        try {
            return block(FoodAi("sk-ant-test", server.url("/").toString().trimEnd('/')))
        } finally {
            request = server.takeRequest()
            body = JSONObject(request.body.readUtf8())
        }
    }

    private fun message(stopReason: String, text: String?) = JSONObject(
        mapOf(
            "id" to "msg_test",
            "type" to "message",
            "role" to "assistant",
            "model" to FoodAi.MODEL,
            "content" to listOfNotNull(
                mapOf("type" to "thinking", "thinking" to "", "signature" to "sig"),
                text?.let { mapOf("type" to "text", "text" to it) },
            ),
            "stop_reason" to stopReason,
            "stop_sequence" to JSONObject.NULL,
            "usage" to mapOf("input_tokens" to 1200, "output_tokens" to 300),
        )
    ).toString()

    @Test
    fun photoRequestHasImageSchemaAndFallback() {
        val meal = """{"items":[{"name":"Milanesa de carne","grams":150,"kcal":330,"protein":32,"carbs":18,"fat":14,"confidence":"media"},
            {"name":"Puré de papas","grams":200,"kcal":226,"protein":4,"carbs":34,"fat":8,"confidence":"alta"}],
            "notes":"Asumí milanesa al horno."}"""

        val result = call(message("end_turn", meal)) { it.estimatePhoto(byteArrayOf(1, 2, 3), "sin aceite", "es") }

        assertEquals("/v1/messages", request.path)
        assertTrue(body.getString("system").contains("Spanish as they're called in Argentina"))
        assertEquals("sk-ant-test", request.getHeader("x-api-key"))
        assertEquals("server-side-fallback-2026-07-01", request.getHeader("anthropic-beta"))
        assertEquals("claude-opus-5-5", body.getString("model"))
        assertEquals("default", body.getString("fallbacks"))
        val output = body.getJSONObject("output_config")
        assertEquals("medium", output.getString("effort"))
        assertEquals("json_schema", output.getJSONObject("format").getString("type"))
        assertEquals(false, output.getJSONObject("format").getJSONObject("schema").getBoolean("additionalProperties"))
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        val image = content.getJSONObject(0)
        assertEquals("image", image.getString("type"))
        assertEquals("image/jpeg", image.getJSONObject("source").getString("media_type"))
        assertEquals("AQID", image.getJSONObject("source").getString("data"))
        assertTrue(content.getJSONObject(1).getString("text").contains("sin aceite"))

        assertEquals(2, result.items.size)
        assertEquals("Milanesa de carne", result.items[0].name)
        assertEquals(150.0, result.items[0].grams, 0.0)
        assertEquals(32.0, result.items[0].macros.protein, 0.0)
        assertEquals("Asumí milanesa al horno.", result.notes)
    }

    @Test
    fun textRequestHasNoImageAndFollowsLanguage() {
        val result = call(message("end_turn", """{"items":[],"notes":"Not food."}""")) { it.estimateText("2 empanadas", "en") }
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals(1, content.length())
        assertEquals("text", content.getJSONObject(0).getString("type"))
        val system = body.getString("system")
        assertTrue(system.contains("plain English"))
        assertTrue(system.contains("in English for the user"))
        assertTrue(!system.contains("Argentina"))
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun labelIsParsedPer100g() {
        val json = """{"name":"Yogur bebible","brand":"Marca","serving_name":"1 vaso","serving_grams":200,
            "kcal_100g":75,"protein_100g":3,"carbs_100g":12,"fat_100g":1.5,"notes":""}"""
        val label = call(message("end_turn", json)) { it.readLabel(byteArrayOf(9)) }
        assertEquals("Yogur bebible", label.name)
        assertEquals(200.0, label.servingGrams, 0.0)
        assertEquals(75.0, label.per100.kcal, 0.0)
    }

    @Test
    fun refusalBecomesFriendlyError() {
        try {
            call(message("refusal", null)) { it.estimatePhoto(byteArrayOf(1)) }
            fail("expected AiException")
        } catch (e: AiException) {
            assertEquals(R.string.ai_err_refusal, e.messageRes)
        }
    }

    @Test
    fun badKeyBecomesFriendlyError() {
        val error = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""
        try {
            call(error, status = 401) { it.estimateText("algo") }
            fail("expected AiException")
        } catch (e: AiException) {
            assertEquals(R.string.ai_err_key, e.messageRes)
        }
    }
}
