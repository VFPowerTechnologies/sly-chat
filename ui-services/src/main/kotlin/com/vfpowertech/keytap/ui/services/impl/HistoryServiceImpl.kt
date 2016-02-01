package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.HistoryService
import java.util.*

class HistoryServiceImpl : HistoryService {
    private val stack = Stack<String>()

    override fun push(url: String) {
        stack.push(url)
    }

    override fun pop(): String =
        stack.pop()

    override fun peek(): String =
        stack.peek()
}