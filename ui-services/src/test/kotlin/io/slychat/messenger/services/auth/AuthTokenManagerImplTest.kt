package io.slychat.messenger.services.auth

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.UnauthorizedException
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.randomAuthToken
import io.slychat.messenger.core.randomSlyAddress
import io.slychat.messenger.services.contacts.TimerFactory
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.TestException
import io.slychat.messenger.testutils.thenResolve
import io.slychat.messenger.testutils.thenReject
import nl.komponents.kovenant.Promise
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import rx.subjects.PublishSubject
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

//TODO this doesn't test the map or the bind/mapUi variants
class AuthTokenManagerImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    @JvmField
    @Rule
    val timeoutRule = Timeout(500)

    val tokenEvents: PublishSubject<TokenEvent> = PublishSubject.create()

    val address = randomSlyAddress()
    val tokenProvider: TokenProvider = mock()
    val timerFactory: TimerFactory = mock()

    lateinit var tokenManager: AuthTokenManagerImpl

    init {
        whenever(tokenProvider.events).thenReturn(tokenEvents)

        whenever(timerFactory.run(any(), any())).thenResolve(Unit)

        tokenManager = AuthTokenManagerImpl(address, tokenProvider, timerFactory)
    }

    fun emitNewToken() {
        tokenEvents.onNext(TokenEvent.New(randomAuthToken()))
    }

    @Test
    fun `it should not run work while no token is available`() {
        var wasCalled = false

        tokenManager.bind<Unit> {
            wasCalled = true
            Promise.ofSuccess(Unit)
        }

        assertFalse(wasCalled, "Work was run")
    }

    @Test
    fun `it should run queued work on receiving a TokenEvent New event`() {
        val v = 1

        val p = tokenManager.bind<Int> {
            Promise.ofSuccess(v)
        }

        emitNewToken()

        assertEquals(v, p.get(), "Invalid value")
    }

    @Test
    fun `it should pass the right credentials to queued work`() {
        val authToken = randomAuthToken()
        tokenEvents.onNext(TokenEvent.New(authToken))

        val creds = UserCredentials(address, authToken)

        val got = tokenManager.bind<UserCredentials> { Promise.ofSuccess(it) }.get()

        assertEquals(creds, got, "Invalid credentials")
    }

    @Test
    fun `it should notify the TokenProvider when an UnauthorizedException is raised`() {
        emitNewToken()

        tokenManager.bind<Unit> { throw UnauthorizedException() }

        verify(tokenProvider).invalidateToken()
    }

    @Test
    fun `it should not notity the TokenProvider for exceptions that are not UnauthorizedException`() {
        emitNewToken()

        assertFailsWith(TestException::class) {
            tokenManager.bind<Unit> { throw TestException() }.get()
        }

        verify(tokenProvider, never()).invalidateToken()
    }

    @Test
    fun `it should retry up to MAX_RETRIES times when an UnauthorizedException is thrown`() {
        val p = tokenManager.bind<Unit> { throw UnauthorizedException() }

        (0..AuthTokenManagerImpl.MAX_RETRIES).forEach {
            emitNewToken()
        }

        assertFailsWith(UnauthorizedException::class) {
            p.get()
        }

        verify(tokenProvider, times(AuthTokenManagerImpl.MAX_RETRIES)).invalidateToken()
    }

    @Test
    fun `receiving a TokenEvent Error event should fail queued work`() {
        val p = tokenManager.bind<Unit> { Promise.ofSuccess(Unit) }

        tokenEvents.onNext(TokenEvent.Error(TestException()))

        assertFailsWith(TestException::class) {
            p.get()
        }
    }
}