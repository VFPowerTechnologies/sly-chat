package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.services.ui.HistoryService
import java.util.*

class DummyHistoryService : HistoryService {
    private val stack = Stack<String>()

    override fun push(url: String) {
        stack.push(url)
    }

    override fun pop(): String =
        stack.pop()

    override fun peek(): String =
        stack.peek()

    override fun clear(): Unit {
        stack.clear()
    }
}