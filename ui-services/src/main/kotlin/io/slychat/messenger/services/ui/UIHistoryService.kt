package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

/**
 * Exposes a stack for keeping track of navigation history.
 */
@JSToJavaGenerate("HistoryService")
interface UIHistoryService {
    /** Push a new value onto the stack. */
    fun push(url: String): Unit
    /** Pop the first value value off the stack. */
    fun pop(): String
    /**
     * Replaces the current history with the given list.
     *
     * The first element of the list should contain the bottom most stack element, with the last element being the top of the stack.
     */
    fun replace(history: List<String>)
    /** Retrieve the first value off the stack without removing it. */
    fun peek(): String
    /** Clears the stack. */
    fun clear(): Unit
}