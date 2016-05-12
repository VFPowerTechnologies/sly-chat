package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("LoadService")
interface UILoadService {
    fun loadComplete()
}
