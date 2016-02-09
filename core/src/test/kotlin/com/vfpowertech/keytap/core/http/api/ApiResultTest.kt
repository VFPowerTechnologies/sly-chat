package com.vfpowertech.keytap.core.http.api

import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiResultTest {
    class R

    @Test
    fun `should throw IllegalArgumentException when given both an error and a response`() {
        assertFailsWith(IllegalArgumentException::class) {
            ApiResult(ApiError("error"), R())
        }
    }

    @Test
    fun `should throw IllegalArgumentException when given neither an error or a response`() {
        assertFailsWith(IllegalArgumentException::class) {
            ApiResult(null,  null)
        }
    }

    @Test
    fun `isError should return true when error is non-null`() {
        assertTrue(ApiResult(ApiError("error"), null).isError)
    }

    @Test
    fun `isError should return false when error is null`() {
        assertFalse(ApiResult(null, R()).isError)
    }
}