package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/** Provides functionality for manipulating the native UI window. */
@JSToJavaGenerate("WindowService")
interface UIWindowService {
    fun minimize()

    fun closeSoftKeyboard(): Promise<Unit, Exception>
}