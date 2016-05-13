package io.slychat.messenger.core.persistence.sqlite

import org.junit.Test
import kotlin.test.assertEquals

class SQLiteUtilsTest {
    @Test
    fun `escapeLikeString should escape the proper characters`() {
        assertEquals("!%!_!!", escapeLikeString("%_!", '!'))
    }
}