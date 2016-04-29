package com.vfpowertech.keytap.android

import com.vfpowertech.keytap.core.UserId

object AndroidPreferences {
    val tokenUserList: String = "tokenUserList"

    fun getTokenSentToServer(userId: UserId): String =
        "${userId.id}/tokenSentToServer"
}