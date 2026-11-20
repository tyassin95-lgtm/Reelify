package app.reelify.ai

import app.reelify.ai.AiTestSupport.creds
import app.reelify.ai.AiTestSupport.http
import app.reelify.ai.AiTestSupport.responsesBody
import app.reelify.integration.common.AiError
import app.reelify.integration.openai.ChatTurn
import app.reelify.integration.openai.OpenAiClient
import app.reelify.integration.openai.S
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class OpenAiClientTest {
    private lateinit var server: MockWebServer
    private val schema = S.obj("answer" to S.str())

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client(key: String? = "sk-test-123456789", attempts: Int = 3) =
        OpenAiClient(http(), creds(openai = key), { "gpt-6.2-sol" }, server.url("/v1/").toString(), attempts, backoffBaseMs = 1)

    @Test fun `sends Responses API request with strict json schema and bearer auth`() = runTest {
        server.enqueue(MockResponse().setBody(responsesBody("""{"answer":"ok"}""")))
        val r = client().structured("SYS", listOf(ChatTurn("user", "hello")), "test_schema", schema)
        assertThat(r.text).isEqualTo("""{"answer":"ok"}""")
        val req = server.takeRequest()
        assertThat(req.path).isEqualTo("/v1/responses")
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer sk-test-123456789")
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertThat(body["model"]!!.jsonPrimitive.content).isEqualTo("gpt-6.2-sol")
        assertThat(body["instructions"]!!.jsonPrimitive.content).isEqualTo("SYS")
        val format = body["text"]!!.jsonObject["format"]!!.jsonObject
        assertThat(format["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
        assertThat(format["strict"]!!.jsonPrimitive.boolean).isTrue()
        assertThat(format["name"]!!.jsonPrimitive.content).isEqualTo("test_schema")
        assertThat(body["input"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("input_text")
        assertThat(body["store"]!!.jsonPrimitive.boolean).isFalse()
    }

    @Test fun `missing key fails fast without network`() = runTest {
        val e = runCatching { client(key = null).structured("S", listOf(ChatTurn("user", "x")), "n", schema) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.MissingKey::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun `401 maps to unauthorized and never leaks the key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Incorrect API key provided: sk-test-123456789"}}"""))
        val e = runCatching { client().structured("S", listOf(ChatTurn("user", "x")), "n", schema) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.Unauthorized::class.java)
        assertThat(e!!.message).doesNotContain("sk-test-123456789")
    }

    @Test fun `429 is retried then succeeds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0").setBody("""{"error":{"message":"rate limit"}}"""))
        server.enqueue(MockResponse().setBody(responsesBody("""{"answer":"after"}""")))
        assertThat(client().structured("S", listOf(ChatTurn("user", "x")), "n", schema).text).contains("after")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun `persistent 429 surfaces RateLimited`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429).setBody("{}")) }
        val e = runCatching { client().structured("S", listOf(ChatTurn("user", "x")), "n", schema) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.RateLimited::class.java)
    }

    @Test fun `5xx is retried`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody(responsesBody("""{"answer":"x"}""")))
        assertThat(client().structured("S", listOf(ChatTurn("user", "x")), "n", schema).text).contains("x")
    }

    @Test fun `400 is not retried`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"Invalid schema"}}"""))
        val e = runCatching { client().structured("S", listOf(ChatTurn("user", "x")), "n", schema) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.BadRequest::class.java)
        assertThat(e!!.message).contains("Invalid schema")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun `refusal and incomplete responses are typed errors`() {
        val c = client()
        val refusal = """{"status":"completed","output":[{"type":"message","content":[{"type":"refusal","refusal":"cannot help"}]}]}"""
        assertThrows(AiError.Refused::class.java) { c.parseResponse(refusal, "m") }
        val incomplete = """{"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[]}"""
        assertThrows(AiError.Incomplete::class.java) { c.parseResponse(incomplete, "m") }
        assertThrows(AiError.Malformed::class.java) { c.parseResponse("not json", "m") }
        assertThrows(AiError.Malformed::class.java) { c.parseResponse("""{"status":"completed","output":[]}""", "m") }
    }

    @Test fun `connectivity failure maps to network error`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        val e = runCatching { client().structured("S", listOf(ChatTurn("user", "x")), "n", schema) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError::class.java)
        assertThat((e as AiError).retryable).isTrue()
    }

    @Test fun `timeout maps to Timeout`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)) }
        val c = OpenAiClient(http(300), creds(), { "m" }, server.url("/v1/").toString(), 1, 1)
        val e = runCatching { c.structured("S", listOf(ChatTurn("user", "x")), "n", schema) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.Timeout::class.java)
    }

    @Test fun `test connection reports missing model distinctly`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        assertThat(client().testConnection()).contains("wasn't found")
        server.enqueue(MockResponse().setBody("""{"id":"gpt-6.2-sol"}"""))
        assertThat(client().testConnection()).contains("Connected")
    }
}
