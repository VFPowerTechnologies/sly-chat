package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("ShareService")
interface UIShareService {
    fun isSupported(): Boolean

    fun inviteToSly(subject: String, text: String, htmlText: String?)
}