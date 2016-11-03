package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.services.VersionCheckResult
import org.junit.Test
import rx.subjects.PublishSubject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UIClientInfoServiceImplTest {
    private class Listener : (VersionCheckResult) -> Unit {
        var result: VersionCheckResult? = null

        override fun invoke(result: VersionCheckResult) {
            this.result = result
        }

        fun assertOutdated() {
            val r = assertNotNull(result, "Listener not called")

            assertFalse(r.isLatest, "Not marked as outdated")
        }

        fun assertUpToDate() {
            val r = assertNotNull(result, "Listener not called")

            assertTrue(r.isLatest, "Marked as outdated")
        }

        fun assertCalledWith(result: VersionCheckResult) {
            val r = assertNotNull(result, "Listener not called")

            assertEquals(result, r, "Listener received invalid result")
        }
    }

    private val dummyVersion = "0.0.0"
    private val outdatedResult = VersionCheckResult(false, dummyVersion)
    private val isUpToDateResult = VersionCheckResult(true, dummyVersion)

    private fun testImmediateCall(result: VersionCheckResult) {
        val subject = PublishSubject.create<VersionCheckResult>()

        val uiClientInfoService = UIClientInfoServiceImpl(subject)

        subject.onNext(result)

        val listener = Listener()

        uiClientInfoService.addVersionOutdatedListener(listener)

        if (result.isLatest)
            listener.assertUpToDate()
        else
            listener.assertOutdated()
    }

    @Test
    fun `it should immediately notify a new listener if the version is marked as being outdated`() {
        testImmediateCall(outdatedResult)
    }

    @Test
    fun `it should immediately notify a new listener if the version is marked as being up to date`() {
        testImmediateCall(isUpToDateResult)
    }

    @Test
    fun `it should notify registered listeners when receiving a result`() {
        val subject = PublishSubject.create<VersionCheckResult>()

        val uiClientInfoService = UIClientInfoServiceImpl(subject)

        val listener = Listener()

        uiClientInfoService.addVersionOutdatedListener(listener)

        subject.onNext(outdatedResult)

        listener.assertCalledWith(outdatedResult)
    }
}