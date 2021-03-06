package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.http.api.versioncheck.CheckResponse
import io.slychat.messenger.core.http.api.versioncheck.ClientVersionAsyncClient
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenResolve
import nl.komponents.kovenant.deferred
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.BehaviorSubject

class HttpVersionCheckerTest {
    companion object {
        @ClassRule
        @JvmField
        val kovenantRule = KovenantTestModeRule()
    }

    private val networkAvailable: BehaviorSubject<Boolean> = BehaviorSubject.create()
    private val clientVersionAsyncClient: ClientVersionAsyncClient = mock()

    private val clientVersionAsyncClientFactory = object : ClientVersionAsyncClientFactory {
        override fun create(): ClientVersionAsyncClient {
            return clientVersionAsyncClient
        }
    }

    private val dummyVersion = "0.0.0"
    private val outdatedResult = VersionCheckResult(false, dummyVersion)
    private val isUpToDateResult = VersionCheckResult(true, dummyVersion)

    private val versionChecker = HttpVersionChecker("0.0.0", networkAvailable, clientVersionAsyncClientFactory)

    @Before
    fun before() {
        whenever(clientVersionAsyncClient.check(any())).thenResolve(CheckResponse(true, dummyVersion))
    }

    fun clientVersionIsOk(ok: Boolean) {
        whenever(clientVersionAsyncClient.check(any())).thenResolve(CheckResponse(ok, dummyVersion))
    }

    fun assertSingleCheck() {
        verify(clientVersionAsyncClient).check(any())
    }

    fun assertNoCheck() {
        verify(clientVersionAsyncClient, never()).check(any())
    }

    fun networkIsAvailable(isAvailable: Boolean) {
        networkAvailable.onNext(isAvailable)
    }

    @Test
    fun `it must perform a version check if network is already available on init`() {
        networkIsAvailable(true)

        versionChecker.init()

        assertSingleCheck()
    }

    @Test
    fun `it must not perform a version check if no network is available on init`() {
        networkIsAvailable(false)

        versionChecker.init()

        assertNoCheck()
    }

    //eg: network comes up, request is issued, then network goes down and back up
    @Test
    fun `it must not perform a version check while one is still running`() {
        val d = deferred<CheckResponse, Exception>()

        whenever(clientVersionAsyncClient.check(any())).thenReturn(d.promise)

        networkIsAvailable(true)

        versionChecker.init()

        networkIsAvailable(false)
        networkIsAvailable(true)

        assertSingleCheck()
    }

    @Test
    fun `it must not perform a version check when the network becomes available if one has already been performed`() {
        networkIsAvailable(true)

        versionChecker.init()

        networkIsAvailable(false)
        networkIsAvailable(true)

        assertSingleCheck()
    }

    @Test
    fun `it must perform a version check when the network becomes available if one has not yet been performed`() {
        versionChecker.init()

        networkIsAvailable(true)

        assertSingleCheck()
    }

    @Test
    fun `it should emit an event when the client version is up to date`() {
        val testSubscriber = versionChecker.versionCheckResult.testSubscriber()

        clientVersionIsOk(true)

        networkIsAvailable(true)

        versionChecker.init()

        Assertions.assertThat(testSubscriber.onNextEvents).apply {
            `as`("Should emit an event")
            containsOnly(isUpToDateResult)
        }
    }

    @Test
    fun `it must emit an event when the client version is out of date`() {
        val testSubscriber = versionChecker.versionCheckResult.testSubscriber()

        clientVersionIsOk(false)

        networkIsAvailable(true)

        versionChecker.init()

        Assertions.assertThat(testSubscriber.onNextEvents).apply {
            `as`("Should emit an event")
            containsOnly(outdatedResult)
        }
    }

    @Test
    fun `it should not run a check if network is available and init has not been called`() {
        networkIsAvailable(true)

        assertNoCheck()
    }
}
