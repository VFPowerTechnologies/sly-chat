package io.slychat.messenger.ios

import apple.uikit.UIActivityItemProvider
import org.moe.natj.general.Pointer
import org.moe.natj.objc.ann.Selector

class EmptyURLActivityItemProvider private constructor(peer: Pointer) : UIActivityItemProvider(peer) {
    companion object {
        @JvmStatic
        @Selector("alloc")
        external fun alloc(): EmptyURLActivityItemProvider
    }

    override fun item(): Any? {
        return null
    }
}