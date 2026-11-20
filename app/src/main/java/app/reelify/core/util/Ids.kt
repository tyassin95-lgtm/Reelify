package app.reelify.core.util

import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

interface Clock {
    fun now(): Long

    object System : Clock {
        override fun now(): Long = java.lang.System.currentTimeMillis()
    }
}
