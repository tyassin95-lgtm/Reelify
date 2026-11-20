package app.reelify.integration.deepgram

import app.reelify.core.model.ReelLanguage
import app.reelify.core.security.AiProvider
import app.reelify.core.security.CredentialRepository
import app.reelify.integration.common.AiError
import app.reelify.integration.common.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

@Serializable
data class DgWord(
    val word: String = "",
    val start: Double = 0.0,
    val end: Double = 0.0,
    val confidence: Double = 0.0,
    @SerialName("punctuated_word") val punctuatedWord: String? = null,
)

@Serializable
data class DgAlternative(val transcript: String = "", val confidence: Double = 0.0, val words: List<DgWord> = emptyList())

@Serializable
data class DgChannel(val alternatives: List<DgAlternative> = emptyList(), @SerialName("detected_language") val detectedLanguage: String? = null)

@Serializable
data class DgResults(val channels: List<DgChannel> = emptyList())

@Serializable
data class DgResponse(val results: DgResults? = null)

data class TranscriptWord(val text: String, val startMs: Long, val endMs: Long, val confidence: Float)

data class Transcript(val text: String, val words: List<TranscriptWord>, val language: ReelLanguage, val model: String)

/**
 * Direct HTTPS client for Deepgram pre-recorded transcription (POST /v1/listen).
 * Only the dialogue audio file for the selected job is uploaded.
 */
class DeepgramClient(
    private val http: OkHttpClient,
    private val credentials: CredentialRepository,
    private val modelProvider: suspend () -> String,
    private val baseUrl: String = "https://api.deepgram.com/v1/",
    private val maxAttempts: Int = 3,
    private val backoffBaseMs: Long = 1500,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildUrl(model: String, language: ReelLanguage, keyterms: List<String>): String {
        val b = (baseUrl + "listen").toHttpUrl().newBuilder()
            .addQueryParameter("model", model)
            .addQueryParameter("language", language.deepgramCode)
            .addQueryParameter("smart_format", "true")
            .addQueryParameter("punctuate", "true")
            .addQueryParameter("filler_words", "false")
        keyterms.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(50).forEach { b.addQueryParameter("keyterm", it) }
        return b.build().toString()
    }

    suspend fun transcribe(audio: File, mime: String, language: ReelLanguage, keyterms: List<String> = emptyList()): Transcript {
        if (!audio.exists() || audio.length() < 512) throw AiError.NoSpeech()
        val key = credentials.key(AiProvider.DEEPGRAM) ?: throw AiError.MissingKey("Deepgram")
        val model = modelProvider()
        return try {
            send(audio, mime, language, model, keyterms, key)
        } catch (e: AiError.BadRequest) {
            // Some model/language combinations don't accept keyterm prompting: retry once without it.
            if (keyterms.isNotEmpty()) send(audio, mime, language, model, emptyList(), key) else throw e
        }
    }

    private suspend fun send(audio: File, mime: String, language: ReelLanguage, model: String, keyterms: List<String>, key: String): Transcript {
        val request = Request.Builder()
            .url(buildUrl(model, language, keyterms))
            .header("Authorization", "Token $key")
            .post(audio.asRequestBody(mime.toMediaType()))
            .build()
        var attempt = 0
        while (true) {
            attempt++
            try {
                val resp = http.newCall(request).await()
                resp.use {
                    val body = it.body.string()
                    if (it.isSuccessful) return parse(body, language, model)
                    val err = mapHttp(it.code, body, it.header("Retry-After"), key)
                    if (!err.retryable || attempt >= maxAttempts) throw err
                    delay((err as? AiError.RateLimited)?.retryAfterSec?.times(1000)?.coerceAtMost(20_000) ?: (backoffBaseMs shl (attempt - 1)))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiError) {
                throw e
            } catch (e: IOException) {
                val err = if (e is SocketTimeoutException || e is InterruptedIOException) AiError.Timeout("Deepgram") else AiError.Network("Deepgram")
                if (attempt >= maxAttempts) throw err
                delay(backoffBaseMs shl (attempt - 1))
            }
        }
    }

    fun parse(body: String, language: ReelLanguage, model: String): Transcript {
        val resp = runCatching { json.decodeFromString(DgResponse.serializer(), body) }.getOrElse { throw AiError.Malformed("Deepgram returned unreadable JSON") }
        val alt = resp.results?.channels?.firstOrNull()?.alternatives?.firstOrNull() ?: throw AiError.NoSpeech()
        val words = alt.words.filter { (it.punctuatedWord ?: it.word).isNotBlank() }.map {
            TranscriptWord((it.punctuatedWord ?: it.word).trim(), (it.start * 1000).toLong(), (it.end * 1000).toLong().coerceAtLeast((it.start * 1000).toLong() + 1), it.confidence.toFloat())
        }.sortedBy { it.startMs }
        if (words.isEmpty() && alt.transcript.isBlank()) throw AiError.NoSpeech()
        return Transcript(alt.transcript, words, language, model)
    }

    suspend fun testConnection(): String {
        val key = credentials.key(AiProvider.DEEPGRAM) ?: throw AiError.MissingKey("Deepgram")
        val req = Request.Builder().url(baseUrl + "projects").header("Authorization", "Token $key").get().build()
        val resp = try { http.newCall(req).await() } catch (e: IOException) { throw AiError.Network("Deepgram") }
        resp.use {
            if (it.isSuccessful) return "Connected to Deepgram"
            throw mapHttp(it.code, it.body.string(), it.header("Retry-After"), key)
        }
    }

    private fun mapHttp(code: Int, body: String, retryAfter: String?, key: String): AiError {
        val msg = runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            (o["err_msg"] ?: o["message"] ?: o["reason"])?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.let { CredentialRepository.redact(it, key) }?.take(200) ?: "HTTP $code"
        return when (code) {
            401 -> AiError.Unauthorized("Deepgram")
            402, 403 -> AiError.Forbidden("Deepgram", msg)
            408 -> AiError.Timeout("Deepgram")
            429 -> AiError.RateLimited("Deepgram", retryAfter?.toLongOrNull())
            in 500..599 -> AiError.Server("Deepgram", code)
            else -> AiError.BadRequest("Deepgram", msg)
        }
    }
}
