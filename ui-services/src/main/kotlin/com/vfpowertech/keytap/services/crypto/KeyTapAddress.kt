package com.vfpowertech.keytap.services.crypto

import com.vfpowertech.keytap.core.UserId
import org.whispersystems.libsignal.SignalProtocolAddress

class KeyTapAddress(val id: UserId) {
    fun toSignalAddress(): SignalProtocolAddress = SignalProtocolAddress(id.toString(), 1)
}