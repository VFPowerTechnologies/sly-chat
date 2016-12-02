package io.slychat.messenger.testutils

/** Exception type to be used in tests. Prevents catching the wrong exception type. */
class TestException : RuntimeException() {
    override fun equals(other: Any?): Boolean {
        return other is TestException
    }

    override fun hashCode(): Int {
        return 0
    }
}
