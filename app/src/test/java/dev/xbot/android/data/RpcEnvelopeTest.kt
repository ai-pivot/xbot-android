package dev.xbot.android.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RpcEnvelopeTest {

    @Test
    fun `unwraps a list result from the REST rpc envelope`() {
        val element = Json.parseToJsonElement(
            """{"ok":true,"data":[{"sub_name":"deepseek","model":"deepseek-chat"}],"error":null}""",
        )

        val unwrapped = unwrapRpcEnvelope(element)

        assertTrue(unwrapped is JsonArray)
        assertEquals(1, (unwrapped as JsonArray).size)
    }

    @Test
    fun `raw array stays untouched`() {
        val element = Json.parseToJsonElement("""[{"model":"deepseek-chat"}]""")
        assertEquals(element, unwrapRpcEnvelope(element))
    }

    @Test
    fun `plain object result stays untouched`() {
        val element = Json.parseToJsonElement("""{"sub_id":"s","model":"deepseek-chat"}""")
        assertEquals(element, unwrapRpcEnvelope(element))
    }
}
