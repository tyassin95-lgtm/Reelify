package app.reelify.ai

import app.reelify.ai.AiTestSupport.creds
import app.reelify.ai.AiTestSupport.http
import app.reelify.core.model.ReelLanguage
import app.reelify.integration.common.AiError
import app.reelify.integration.deepgram.DeepgramClient
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeepgramClientTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun client(key: String? = "dg-test-123456789") = DeepgramClient(http(), creds(deepgram = key), { "nova-3" }, server.url("/v1/").toString(), 2, 1)
    private fun audio() = tmp.newFile("a.m4a").apply { writeBytes(ByteArray(4096) { (it % 251).toByte() }) }

    private val arabicBody = """
        {"metadata":{},"results":{"channels":[{"alternatives":[{"transcript":"شنو الفرق بين إيروليز والليزر","confidence":0.93,
         "words":[{"word":"شنو","start":0.12,"end":0.4,"confidence":0.98,"punctuated_word":"شنو"},
                  {"word":"الفرق","start":0.42,"end":0.8,"confidence":0.97,"punctuated_word":"الفرق"},
                  {"word":"بين","start":0.82,"end":1.0,"confidence":0.95,"punctuated_word":"بين"},
                  {"word":"ايرو","start":1.05,"end":1.3,"confidence":0.5,"punctuated_word":"ايرو"},
                  {"word":"ليز","start":1.31,"end":1.6,"confidence":0.55,"punctuated_word":"ليز"},
                  {"word":"والليزر","start":1.62,"end":2.2,"confidence":0.9,"punctuated_word":"والليزر؟"}]}]}]}}
    """.trimIndent()

    @Test fun `request uses nova-3, ar-IQ, keyterms and Token auth with binary audio`() = runTest {
        server.enqueue(MockResponse().setBody(arabicBody))
        val t = client().transcribe(audio(), "audio/mp4", ReelLanguage.IRAQI_ARABIC, listOf("Aerolase", "إيروليز"))
        val req = server.takeRequest()
        val url = req.requestUrl!!
        assertThat(url.encodedPath).isEqualTo("/v1/listen")
        assertThat(url.queryParameter("model")).isEqualTo("nova-3")
        assertThat(url.queryParameter("language")).isEqualTo("ar-IQ")
        assertThat(url.queryParameterValues("keyterm")).containsExactly("Aerolase", "إيروليز")
        assertThat(req.getHeader("Authorization")).isEqualTo("Token dg-test-123456789")
        assertThat(req.getHeader("Content-Type")).startsWith("audio/mp4")
        assertThat(req.bodySize).isEqualTo(4096)
        assertThat(t.words).hasSize(6)
        assertThat(t.words.last().text).isEqualTo("والليزر؟")
        assertThat(t.words.first().startMs).isEqualTo(120)
    }

    @Test fun `english request uses en`() {
        val url = client().buildUrl("nova-3", ReelLanguage.ENGLISH, emptyList()).toHttpUrl()
        assertThat(url.queryParameter("language")).isEqualTo("en")
        assertThat(url.queryParameter("smart_format")).isEqualTo("true")
    }

    @Test fun `parses english punctuated words with timestamps`() {
        val body = """{"results":{"channels":[{"alternatives":[{"transcript":"Book now.","words":[{"word":"book","start":0.5,"end":0.8,"confidence":0.9,"punctuated_word":"Book"},{"word":"now","start":0.82,"end":1.1,"confidence":0.92,"punctuated_word":"now."}]}]}]}}"""
        val t = client().parse(body, ReelLanguage.ENGLISH, "nova-3")
        assertThat(t.words.map { it.text }).containsExactly("Book", "now.").inOrder()
        assertThat(t.words[1].endMs).isEqualTo(1100)
    }

    @Test fun `empty transcript is NoSpeech`() {
        val body = """{"results":{"channels":[{"alternatives":[{"transcript":"","words":[]}]}]}}"""
        val e = runCatching { client().parse(body, ReelLanguage.ENGLISH, "nova-3") }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.NoSpeech::class.java)
    }

    @Test fun `empty audio is rejected before upload`() = runTest {
        val f = tmp.newFile("empty.m4a")
        val e = runCatching { client().transcribe(f, "audio/mp4", ReelLanguage.ENGLISH) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.NoSpeech::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test fun `401 is unauthorized`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"err_msg":"Invalid credentials."}"""))
        val e = runCatching { client().transcribe(audio(), "audio/mp4", ReelLanguage.ENGLISH) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.Unauthorized::class.java)
    }

    @Test fun `keyterm rejection falls back to a request without keyterms`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"err_msg":"keyterm not supported for this language"}"""))
        server.enqueue(MockResponse().setBody(arabicBody))
        val t = client().transcribe(audio(), "audio/mp4", ReelLanguage.IRAQI_ARABIC, listOf("Aerolase"))
        assertThat(t.words).isNotEmpty()
        server.takeRequest()
        assertThat(server.takeRequest().requestUrl!!.queryParameterValues("keyterm")).isEmpty()
    }

    @Test fun `network interruption surfaces a retryable error`() = runTest {
        repeat(2) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)) }
        val e = runCatching { client().transcribe(audio(), "audio/mp4", ReelLanguage.ENGLISH) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError::class.java)
        assertThat((e as AiError).retryable).isTrue()
    }

    @Test fun `missing key`() = runTest {
        val e = runCatching { client(key = null).transcribe(audio(), "audio/mp4", ReelLanguage.ENGLISH) }.exceptionOrNull()
        assertThat(e).isInstanceOf(AiError.MissingKey::class.java)
    }
}
