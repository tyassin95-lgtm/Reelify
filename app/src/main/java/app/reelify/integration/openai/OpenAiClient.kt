package app.reelify.integration.openai

import app.reelify.core.security.AiProvider
import app.reelify.core.security.CredentialRepository
import app.reelify.integration.common.AiError
import app.reelify.integration.common.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

data class ChatTurn(val role: String, val text: String)

data class StructuredResult(val text: String, val model: String, val responseId: String?)

/**
 * Direct HTTPS client for the OpenAI Responses API (POST /v1/responses) using strict
 * json_schema structured outputs. No backend is involved.
 */
class OpenAiClient(
    private val http: OkHttpClient,
    private val credentials: CredentialRepository,
    private val modelProvider: suspend () -> String,
    private val baseUrl: String = "https://api.openai.com/v1/",
    private val maxAttempts: Int = 3,
    private val backoffBaseMs: Long = 1200,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaTypeCompat()

    fun buildRequestBody(model: String, instructions: String, turns: List<ChatTurn>, schemaName: String, schema: JsonObject, maxOutputTokens: Int): JsonObject =
        buildJsonObject {
            put("model", model)
            put("instructions", instructions)
            put("input", buildJsonArray {
                turns.forEach { t ->
                    add(buildJsonObject {
                        put("role", t.role)
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", if (t.role == "assistant") "output_text" else "input_text")
                                put("text", t.text)
                            })
                        })
                    })
                }
            })
            put("text", buildJsonObject {
                put("format", buildJsonObject {
                    put("type", "json_schema")
                    put("name", schemaName)
                    put("strict", true)
                    put("schema", schema)
                })
            })
            put("max_output_tokens", maxOutputTokens)
            put("store", false)
        }

    suspend fun structured(
        instructions: String,
        turns: List<ChatTurn>,
        schemaName: String,
        schema: JsonObject,
        maxOutputTokens: Int = 6000,
    ): StructuredResult {
        val key = credentials.key(AiProvider.OPENAI) ?: throw AiError.MissingKey("OpenAI")
        val model = modelProvider()
        val body = buildRequestBody(model, instructions, turns, schemaName, schema, maxOutputTokens).toString()
        val request = Request.Builder()
            .url(baseUrl + "responses")
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMedia))
            .build()
        val raw = executeWithRetry(request, key)
        return parseResponse(raw, model)
    }

    /** Validates the key (and whether the configured model is visible to it). */
    suspend fun testConnection(): String {
        val key = credentials.key(AiProvider.OPENAI) ?: throw AiError.MissingKey("OpenAI")
        val model = modelProvider()
        val req = Request.Builder().url(baseUrl + "models/" + model).header("Authorization", "Bearer $key").get().build()
        val resp = try { http.newCall(req).await() } catch (e: IOException) { throw mapIo(e) }
        resp.use {
            return when (it.code) {
                200 -> "Connected · model \"$model\" available"
                404 -> "Key is valid, but model \"$model\" wasn't found for this account. Change it in Advanced settings."
                else -> throw mapHttp(it.code, it.body.string(), it.header("Retry-After"), key)
            }
        }
    }

    internal fun parseResponse(raw: String, model: String): StructuredResult {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse { throw AiError.Malformed("not JSON") }
        root["error"]?.takeIf { it is JsonObject }?.let { err ->
            val msg = err.jsonObject["message"]?.jsonPrimitive?.contentOrNull ?: "unknown error"
            throw AiError.BadRequest("OpenAI", msg.take(200))
        }
        val status = root["status"]?.jsonPrimitive?.contentOrNull
        val output = root["output"] as? JsonArray ?: JsonArray(emptyList())
        val texts = StringBuilder()
        var refusal: String? = null
        for (item in output) {
            val obj = item as? JsonObject ?: continue
            if (obj["type"]?.jsonPrimitive?.contentOrNull != "message") continue
            val content = obj["content"] as? JsonArray ?: continue
            for (c in content) {
                val co = c as? JsonObject ?: continue
                when (co["type"]?.jsonPrimitive?.contentOrNull) {
                    "output_text" -> texts.append(co["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
                    "refusal" -> refusal = co["refusal"]?.jsonPrimitive?.contentOrNull
                }
            }
        }
        if (refusal != null) throw AiError.Refused(refusal!!.take(240))
        if (status == "incomplete") {
            val reason = root["incomplete_details"]?.let { (it as? JsonObject)?.get("reason")?.jsonPrimitive?.contentOrNull } ?: "incomplete"
            throw AiError.Incomplete(reason)
        }
        if (status == "failed") throw AiError.Server("OpenAI", 500)
        if (texts.isBlank()) throw AiError.Malformed("empty output")
        return StructuredResult(texts.toString(), root["model"]?.jsonPrimitive?.contentOrNull ?: model, root["id"]?.jsonPrimitive?.contentOrNull)
    }

    private suspend fun executeWithRetry(request: Request, key: String): String {
        var attempt = 0
        var last: AiError? = null
        while (attempt < maxAttempts) {
            attempt++
            try {
                val resp = http.newCall(request).await()
                resp.use {
                    val text = it.body.string()
                    if (it.isSuccessful) return text
                    val err = mapHttp(it.code, text, it.header("Retry-After"), key)
                    if (!err.retryable || attempt >= maxAttempts) throw err
                    last = err
                    val wait = (err as? AiError.RateLimited)?.retryAfterSec?.times(1000)?.coerceAtMost(20_000) ?: (backoffBaseMs shl (attempt - 1))
                    delay(wait)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiError) {
                throw e
            } catch (e: IOException) {
                val err = mapIo(e)
                if (attempt >= maxAttempts) throw err
                last = err
                delay(backoffBaseMs shl (attempt - 1))
            }
        }
        throw last ?: AiError.Network("OpenAI")
    }

    private fun mapIo(e: IOException): AiError =
        if (e is SocketTimeoutException || e is InterruptedIOException) AiError.Timeout("OpenAI") else AiError.Network("OpenAI")

    private fun mapHttp(code: Int, body: String, retryAfter: String?, key: String): AiError {
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.let { CredentialRepository.redact(it, key) }?.take(220) ?: "HTTP $code"
        return when (code) {
            401 -> AiError.Unauthorized("OpenAI")
            403 -> AiError.Forbidden("OpenAI", message)
            408 -> AiError.Timeout("OpenAI")
            429 -> AiError.RateLimited("OpenAI", retryAfter?.toLongOrNull())
            in 500..599 -> AiError.Server("OpenAI", code)
            else -> AiError.BadRequest("OpenAI", message)
        }
    }
}

internal fun String.toMediaTypeCompat() = okhttp3.MediaType.Companion.run { this@toMediaTypeCompat.toMediaType() }
