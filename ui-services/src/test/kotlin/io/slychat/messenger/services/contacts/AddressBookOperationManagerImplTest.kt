package io.slychat.messenger.services.contacts

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.TestException
import io.slychat.messenger.testutils.cond
import io.slychat.messenger.testutils.testSubscriber
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressBookOperationManagerImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        class MockSyncJobFactory : AddressBookSyncJobFactory {
            val jobs = ArrayList<AddressBookSyncJob>()
            val deferreds = ArrayList<Deferred<AddressBookSyncResult, Exception>>()
            override fun create(): AddressBookSyncJob {
                val job = mock<AddressBookSyncJob>()
                jobs.add(job)

                val d = deferred<AddressBookSyncResult, Exception>()
                deferreds.add(d)

                whenever(job.run(any())).thenReturn(d.promise)

                return job
            }
        }

        class MockSyncScheduler : SyncScheduler {
            private val scheduledEventSubject: PublishSubject<Unit> = PublishSubject.create()

            override val scheduledEvent: Observable<Unit> = scheduledEventSubject

            var wasScheduled = false

            override fun schedule() {
                wasScheduled = true
            }

            fun emitEvent() {
                scheduledEventSubject.onNext(Unit)
            }
        }
    }

    val factory: AddressBookSyncJobFactory = mock()
    val addressBookJob: AddressBookSyncJob = mock()
    val jobDeferred = deferred<AddressBookSyncResult, Exception>()

    val networkStatus: BehaviorSubject<Boolean> = BehaviorSubject.create()

    @Before
    fun before() {
        whenever(factory.create()).thenReturn(addressBookJob)
        whenever(addressBookJob.run(any())).thenReturn(jobDeferred.promise)
    }

    fun createRunner(
        isNetworkAvailable: Boolean = false,
        jobFactory: AddressBookSyncJobFactory = factory,
        syncScheduler: SyncScheduler = ImmediateSyncScheduler()
    ): AddressBookOperationManagerImpl {
        networkStatus.onNext(isNetworkAvailable)

        return AddressBookOperationManagerImpl(
            networkStatus,
            jobFactory,
            syncScheduler
        )
    }

    fun doLocalSync(runner: AddressBookOperationManagerImpl) {
        runner.withCurrentSyncJob { doFindPlatformContacts() }
    }

    fun successUnit(): Promise<Unit, Exception> = Promise.ofSuccess(Unit)

    @Test
    fun `it should run a sync job if no pending operations are running and the network is available`() {
        val runner = createRunner(true)

        doLocalSync(runner)

        verify(addressBookJob).run(any())
    }

    @Test
    fun `it should not run a sync job if no pending operations are running and the network is unavailable`() {
        val runner = createRunner()

        doLocalSync(runner)

        verify(addressBookJob, never()).run(any())
    }

    @Test
    fun `it should run an operation if no sync job is running`() {
        val runner = createRunner()

        var wasRun = false
        runner.runOperation {
            wasRun = true
            successUnit()
        }

        assertTrue(wasRun, "Operation was not run")
    }

    @Test
    fun `it should run pending operations after a sync has completed`() {
        val runner = createRunner(true)

        doLocalSync(runner)

        jobDeferred.resolve(AddressBookSyncResult(true, 0, false, emptySet()))

        var wasRun = false
        runner.runOperation {
            wasRun = true
            successUnit()
        }

        assertTrue(wasRun, "Operation was not run")
    }

    @Test
    fun `it should run a sync job after available pending operations are complete`() {
        val runner = createRunner(true)

        val d = deferred<Unit, Exception>()
        runner.runOperation { d.promise }

        doLocalSync(runner)

        d.resolve(Unit)

        verify(addressBookJob).run(any())
    }

    @Test
    fun `it should queue operations if one is already running`() {
        val runner = createRunner(true)

        val d = deferred<Unit, Exception>()
        runner.runOperation { d.promise }

        var wasRun = false
        runner.runOperation {
            wasRun = true
            successUnit()
        }

        assertFalse(wasRun, "Operation wasn't queued")
    }

    @Test
    fun `it should queue operations if a sync job is running`() {
        val runner = createRunner(true)

        doLocalSync(runner)

        var wasRun = false
        runner.runOperation {
            wasRun = true
            successUnit()
        }

        assertFalse(wasRun, "Operation wasn't queued")
    }

    @Test
    fun `it should not run a queued sync job if the network comes back online and an operation is pending`() {
        val runner = createRunner(false)

        val d = deferred<Unit, Exception>()
        runner.runOperation { d.promise }

        doLocalSync(runner)

        networkStatus.onNext(true)

        verify(addressBookJob, never()).run(any())
    }

    @Test
    fun `it should run the next operation after an operation fails`() {
        val runner = createRunner()

        val d = deferred<Unit, Exception>()
        runner.runOperation { d.promise }

        var wasRun = false
        runner.runOperation {
            wasRun = true
            successUnit()
        }

        d.reject(RuntimeException("test error"))

        assertTrue(wasRun, "Operation wasn't run")
    }

    @Test
    fun `it should run a queued sync job when the network comes back online`() {
        val runner = createRunner()

        doLocalSync(runner)

        networkStatus.onNext(true)

        verify(addressBookJob).run(any())
    }

    //operation -> sync job
    @Test
    fun `it should not run a queued sync job when no network is available`() {
        val runner = createRunner()

        val d = deferred<Unit, Exception>()
        runner.runOperation { d.promise }

        doLocalSync(runner)

        d.resolve(Unit)

        verify(addressBookJob, never()).run(any())
    }

    @Test
    fun `it should queue a sync job if one is already running`() {
        val factory = MockSyncJobFactory()

        val runner = createRunner(true, factory)

        doLocalSync(runner)
        doLocalSync(runner)

        verify(factory.jobs[0]).run(any())
        assertEquals(1, factory.jobs.size, "More than one concurrent job created")
    }

    @Test
    fun `it should run a queued sync job after the current one is complete if no operations are pending`() {
        val factory = MockSyncJobFactory()

        val runner = createRunner(true, factory)

        doLocalSync(runner)
        doLocalSync(runner)

        factory.deferreds[0].resolve(AddressBookSyncResult())

        assertEquals(2, factory.jobs.size, "Second job not created")
        verify(factory.jobs[1]).run(any())
    }

    @Test
    fun `it should emit a running event when a sync begins`() {
        val runner = createRunner(true)

        val testSubscriber = runner.syncEvents.testSubscriber()

        doLocalSync(runner)

        val events = testSubscriber.onNextEvents

        assertThat(events)
            .haveExactly(1, cond("isRunning") { it is AddressBookSyncEvent.Begin })
            .`as`("Running events")
    }

    @Test
    fun `it should emit a a stopped event when a sync ends`() {
        val factory = MockSyncJobFactory()

        val runner = createRunner(true, factory)

        val testSubscriber = runner.syncEvents.testSubscriber()

        doLocalSync(runner)

        factory.deferreds[0].resolve(AddressBookSyncResult(true, 0, false))

        val events = testSubscriber.onNextEvents

        assertThat(events)
            .haveExactly(1, cond("!isRunning") { it is AddressBookSyncEvent.End })
            .`as`("Running events")
    }

    @Test(timeout = 300)
    fun `the promise returned by addOperation should be resolved with the value returned by the operation`() {
        val runner = createRunner(true)

        val d = deferred<Int, Exception>()
        val p = runner.runOperation {
            d.promise
        }

        val v = 5
        d.resolve(v)

        assertEquals(p.get(), v, "Invalid value")
    }

    @Test(timeout = 300)
    fun `the promise returned by addOperation should be rejected with the exception thrown by the operation`() {
        val runner = createRunner(true)

        val d = deferred<Int, Exception>()
        val p = runner.runOperation {
            d.promise
        }

        d.reject(TestException())

        assertFailsWith(TestException::class) {
            p.get()
        }
    }

    @Test
    fun `it should not run a sync job until the SyncScheduler emits an event`() {
        val scheduler = MockSyncScheduler()
        val runner = createRunner(true, syncScheduler = scheduler)

        doLocalSync(runner)
        doLocalSync(runner)

        verify(addressBookJob, never()).run(any())

        scheduler.emitEvent()

        verify(addressBookJob).run(any())
    }

    @Test
    fun `it should run operations after scheduling a sync so long as no schedule event has occured`() {
        val scheduler = MockSyncScheduler()
        val runner = createRunner(true, syncScheduler = scheduler)

        doLocalSync(runner)

        val d = deferred<Int, Exception>()
        var wasRun = false
        runner.runOperation {
            wasRun = true
            d.promise
        }

        assertTrue(wasRun, "Operation not run")
    }

    //this feels pretty redundant, but the other test doesn't actually test the syncscheduler
    @Test
    fun `it should start a schedule sync once an operation completes`() {
        val scheduler = MockSyncScheduler()
        val runner = createRunner(true, syncScheduler = scheduler)

        doLocalSync(runner)

        val d = deferred<Unit, Exception>()
        runner.runOperation {
            d.promise
        }

        scheduler.emitEvent()

        verify(addressBookJob, never()).run(any())

        d.resolve(Unit)

        verify(addressBookJob).run(any())
    }

    @Test
    fun `withCurrentSyncJobNoScheduler should bypass the scheduler`() {
        val scheduler = MockSyncScheduler()
        val runner = createRunner(true, syncScheduler = scheduler)

        runner.withCurrentSyncJobNoScheduler { doPull() }

        verify(addressBookJob).run(any())
    }
}
