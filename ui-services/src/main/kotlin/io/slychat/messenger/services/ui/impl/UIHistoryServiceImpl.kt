package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.ui.UIHistoryService
import java.util.*

class UIHistoryServiceImpl: UIHistoryService {
    private val stack = Stack<String>()

    override fun push(url: String) {
        stack.push(url)
    }

    override fun pop(): String {
        if (stack.empty())
            return ""
        else {
            return stack.pop()
        }
    }

    override fun replace(history: List<String>) {
        stack.clear()
        stack.addAll(history)
    }

    override fun peek(): String =
        stack.peek()

    override fun clear(): Unit {
        stack.clear()
    }
}