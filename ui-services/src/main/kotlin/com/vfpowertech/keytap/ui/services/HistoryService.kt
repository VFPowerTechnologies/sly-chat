package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

/**
 * Exposes a stack for keeping track of navigation history.
 */
@JSToJavaGenerate
interface HistoryService {
    /** Push a new value onto the stack. */
    fun push(url: String): Unit
    /** Pop the first value value off the stack. */
    fun pop(): String
    /** Retrieve the first value off the stack without removing it. */
    fun peek(): String
}