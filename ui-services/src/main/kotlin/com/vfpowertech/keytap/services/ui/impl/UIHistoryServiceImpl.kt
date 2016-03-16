package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.services.ui.UIHistoryService
import java.util.Stack

class UIHistoryServiceImpl: UIHistoryService {
    private val stack = Stack<String>()

    override fun push(url: String) {
        stack.push(url)
    }

    override fun pop(): String {
        if(stack.empty())
            return ""
        else
            return stack.pop()
    }

    override fun peek(): String =
        stack.peek()

    override fun clear(): Unit {
        stack.clear()
    }
}