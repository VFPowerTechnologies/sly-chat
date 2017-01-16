package io.slychat.messenger.core

import apple.uikit.UIDevice

class OSInfo {
    companion object {
        @JvmStatic
        val type: Os.Type
            get() = Os.Type.IOS

        @JvmStatic
        val version: String
            get() = UIDevice.currentDevice().systemVersion()
    }
}