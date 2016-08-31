package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.persistence.SessionDataPersistenceManager
import io.slychat.messenger.core.randomAuthToken
import io.slychat.messenger.testutils.thenResolve
import org.junit.Before
import org.junit.Test

class SessionDataManagerImplTest {
    val sessionDataPersistenceManager = mock<SessionDataPersistenceManager>()
    val sessionDataManager = SessionDataManagerImpl(sessionDataPersistenceManager)

    @Before
    fun before() {
        whenever(sessionDataPersistenceManager.store(any())).thenResolve(Unit)
    }

    @Test
    fun `it should write the changes if they differed from the cached version`() {
        val updated = sessionDataManager.sessionData.copy(authToken = randomAuthToken())

        sessionDataManager.update(updated).get()

        verify(sessionDataPersistenceManager).store(updated)
    }
}