package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.services.ui.UIHistoryService
import java.util.*

class DummyUIHistoryService : UIHistoryService {
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