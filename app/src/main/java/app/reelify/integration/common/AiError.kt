package app.reelify.integration.common

/** Typed, user-presentable failures for external AI/transcription providers. Never contains secrets. */
sealed class AiError(message: String, val retryable: Boolean) : Exception(message) {
    class MissingKey(provider: String) : AiError("$provider isn't configured. Add an API key in Settings → AI Connections.", false)
    class Unauthorized(provider: String) : AiError("$provider rejected the API key. Check or replace it in Settings.", false)
    class Forbidden(provider: String, detail: String) : AiError("$provider denied the request: $detail", false)
    class RateLimited(provider: String, val retryAfterSec: Long?) : AiError("$provider rate limit or quota reached. Try again ${retryAfterSec?.let { "in ${it}s" } ?: "shortly"}.", true)
    class Timeout(provider: String) : AiError("$provider took too long to respond. Check your connection and retry.", true)
    class Network(provider: String) : AiError("Couldn't reach $provider. Check your internet connection.", true)
    class Server(provider: String, val code: Int) : AiError("$provider is having trouble (HTTP $code). Retry in a moment.", true)
    class BadRequest(provider: String, detail: String) : AiError("$provider couldn't process the request: $detail", false)
    class Malformed(detail: String) : AiError("The AI response was invalid ($detail). Please retry.", true)
    class Refused(detail: String) : AiError("The AI declined this request: $detail", false)
    class Incomplete(detail: String) : AiError("The AI response was cut short ($detail). Please retry.", true)
    class NoSpeech : AiError("No speech was recognized in the dialogue audio.", false)
}
