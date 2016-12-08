package io.slychat.messenger.core

import android.os.Build

class OSInfo {
    companion object {
        @JvmStatic
        val type: Os.Type
            get() = Os.Type.ANDROID

        @JvmStatic
        val version: String
            get() = Build.VERSION.RELEASE
    }
}