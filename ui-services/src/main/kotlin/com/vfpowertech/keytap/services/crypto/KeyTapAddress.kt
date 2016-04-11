package com.vfpowertech.keytap.services.crypto

import org.whispersystems.libsignal.SignalProtocolAddress

class KeyTapAddress(val username: String) {
    fun toSignalAddress(): SignalProtocolAddress = SignalProtocolAddress(username, 1)
}