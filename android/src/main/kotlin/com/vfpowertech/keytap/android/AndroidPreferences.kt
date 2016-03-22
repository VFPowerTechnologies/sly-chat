package com.vfpowertech.keytap.android

object AndroidPreferences {
    val tokenUserList: String = "tokenUserList"

    fun getTokenSentToServer(username: String): String =
        "$username/tokenSentToServer"
}