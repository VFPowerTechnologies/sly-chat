package com.vfpowertech.keytap.core

import org.whispersystems.libsignal.SignalProtocolAddress

class KeyTapAddress(val id: UserId, val deviceId: Int) {
    fun toSignalAddress(): SignalProtocolAddress = SignalProtocolAddress(id.id.toString(), deviceId)
}