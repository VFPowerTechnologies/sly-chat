package io.slychat.messenger.android

import io.slychat.messenger.core.UserId

object AndroidPreferences {
    val tokenUserList: String = "tokenUserList"

    fun getTokenSentToServer(userId: UserId): String =
        "${userId.long}/tokenSentToServer"
}