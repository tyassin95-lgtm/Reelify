package app.reelify.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AiProvider(val label: String, val storeKey: String) {
    OPENAI("OpenAI", "openai_api_key"),
    DEEPGRAM("Deepgram", "deepgram_api_key"),
}

enum class CredentialSource { USER, EMBEDDED, NONE }

data class CredentialStatus(val provider: AiProvider, val source: CredentialSource, val masked: String) {
    val configured: Boolean get() = source != CredentialSource.NONE
}

/**
 * Resolves API credentials. Priority: user-entered (Keystore-encrypted) > embedded build-time key.
 * Keys are only ever exposed to the HTTP layer; [CredentialStatus.masked] is safe to display.
 */
class CredentialRepository(
    private val store: SecretStore,
    private val embedded: Map<AiProvider, String>,
) {
    private val _statuses = MutableStateFlow(compute())
    val statuses: StateFlow<Map<AiProvider, CredentialStatus>> = _statuses.asStateFlow()

    fun key(provider: AiProvider): String? =
        store.get(provider.storeKey)?.takeIf { it.isNotBlank() }
            ?: embedded[provider]?.takeIf { it.isNotBlank() }

    fun status(provider: AiProvider): CredentialStatus = _statuses.value.getValue(provider)

    fun setUserKey(provider: AiProvider, value: String) {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Key is empty" }
        store.put(provider.storeKey, trimmed)
        _statuses.value = compute()
    }

    fun removeUserKey(provider: AiProvider) {
        store.remove(provider.storeKey)
        _statuses.value = compute()
    }

    private fun compute(): Map<AiProvider, CredentialStatus> = AiProvider.entries.associateWith { p ->
        val user = store.get(p.storeKey)?.takeIf { it.isNotBlank() }
        val emb = embedded[p]?.takeIf { it.isNotBlank() }
        when {
            user != null -> CredentialStatus(p, CredentialSource.USER, mask(user))
            emb != null -> CredentialStatus(p, CredentialSource.EMBEDDED, mask(emb))
            else -> CredentialStatus(p, CredentialSource.NONE, "")
        }
    }

    companion object {
        fun mask(key: String): String = if (key.length <= 8) "••••" else key.take(4) + "••••" + key.takeLast(4)

        /** Removes anything that looks like a credential from text before it's shown or logged. */
        fun redact(text: String, vararg secrets: String?): String {
            var out = text
            secrets.filterNotNull().filter { it.length >= 6 }.forEach { out = out.replace(it, "[redacted]") }
            out = out.replace(Regex("(sk-[A-Za-z0-9_\\-]{6})[A-Za-z0-9_\\-]+"), "$1…")
            out = out.replace(Regex("(?i)(authorization\\s*[:=]\\s*)(bearer|token)?\\s*\\S+"), "$1[redacted]")
            return out
        }
    }
}
