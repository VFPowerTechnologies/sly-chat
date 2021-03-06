package io.slychat.messenger.core.sentry

data class DSN(
    val publicKey: String,
    val privateKey: String,
    val projectId: String,
    val scheme: String,
    val host: String,
    val port: Int
) {
    fun getStoreUrl(): String {
        val port = if (this.port == -1) "" else ":$port"
        return "$scheme://$host$port/api/$projectId/store/"
    }
}