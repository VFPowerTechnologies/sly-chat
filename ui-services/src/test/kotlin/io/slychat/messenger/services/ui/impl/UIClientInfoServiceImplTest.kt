package io.slychat.messenger.services.ui.impl

import org.junit.Test
import rx.subjects.PublishSubject
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UIClientInfoServiceImplTest {
    class Listener : () -> Unit {
        private var wasCalled = false

        override fun invoke() {
            wasCalled = true
        }

        fun assertCalled() {
            assertTrue(wasCalled, "Listener not called")
        }

        fun assertNotCalled() {
            assertFalse(wasCalled, "Listener called")
        }
    }

    @Test
    fun `it should call a listener if the version is marked as being outdated`() {
        val subject = PublishSubject.create<Unit>()

        val uiClientInfoService = UIClientInfoServiceImpl(subject)

        subject.onNext(Unit)

        val listener = Listener()

        uiClientInfoService.addVersionOutdatedListener(listener)

        listener.assertCalled()
    }

    @Test
    fun `it should not call a listener if the version is not outdated`() {
        val subject = PublishSubject.create<Unit>()

        val uiClientInfoService = UIClientInfoServiceImpl(subject)

        val listener = Listener()

        uiClientInfoService.addVersionOutdatedListener(listener)

        listener.assertNotCalled()
    }
}