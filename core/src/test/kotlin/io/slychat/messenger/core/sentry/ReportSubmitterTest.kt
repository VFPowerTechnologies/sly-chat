package io.slychat.messenger.core.sentry

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions
import org.joda.time.DateTimeUtils
import org.junit.Test
import org.mockito.ArgumentMatcher
import org.mockito.Matchers
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportSubmitterTest {
    private companion object {
        class WorkerStopped() : Throwable()

        class CollectionWith<E>(private val expectedItems: Collection<E>) : ArgumentMatcher<Collection<E>> {
            override fun matches(argument: Any?): Boolean {
                return when (argument) {
                    null -> false
                    is Collection<*> -> {
                        if (argument.size != expectedItems.size)
                            false
                        else {
                            //simple but inefficient
                            argument.toList() == expectedItems.toList()
                        }
                    }
                    else -> false
                }
            }

            override fun toString(): String =
                expectedItems.toString()
        }
    }

    private inline fun <reified T : Any> argThat(argumentMatcher: ArgumentMatcher<T>) = Matchers.argThat<T>(argumentMatcher) ?: createInstance(T::class)

    private val dummyReport = 0
    private val dummyReports = listOf(1, 2)

    private val dummyFatalError = ReportSubmitError.Fatal("FATAL", RuntimeException("Fatal exception"))
    private val dummyRecoverableError = ReportSubmitError.Recoverable("RECOVERABLE", RuntimeException("Recoverable exception"))

    private lateinit var mockClient: ReportSubmitClient<Int>
    private lateinit var mockStorage : ReportStorage<Int>
    private lateinit var reporter: ReportSubmitter<Int>
    private lateinit var throwableQueue: BlockingQueue<Throwable?>

    private fun <R> withFixedMillis(millis: Long, body: () -> R): R {
        DateTimeUtils.setCurrentMillisFixed(millis)
        return try {
            body()
        }
        finally {
            DateTimeUtils.setCurrentMillisSystem()
        }
    }

    private fun sendReport(report: Int) {
        reporter.submit(report)
    }

    private fun sendReports(n: Int) {
        (1..n).forEach { i ->
            reporter.submit(i)
        }
    }

    private fun processReport(report: Int) {
         processReports(listOf(report))
    }

    private fun processReports(n: Int) {
        (1..n).forEach { processReport(dummyReport) }
    }

    private fun processReports(reports: Iterable<Int>) {
        reports.forEach { reporter.processMessage(ReporterMessage.BugReport(it)) }
    }

    private fun processNetworkStatus(isAvailable: Boolean) {
        reporter.processMessage(ReporterMessage.NetworkStatus(isAvailable))
    }

    private fun sendShutdown() {
        reporter.shutdown()
    }

    private fun processShutdown() {
        reporter.processMessage(ReporterMessage.Shutdown())
    }

    private fun initReporter(
        isNetworkAvailable: Boolean = false,
        isFatal: Boolean = false,
        initialWaitTimeMs: Long = ReportSubmitter.DEFAULT_INITIAL_WAIT_TIME_MS,
        maxWaitTimeMs: Long = ReportSubmitter.DEFAULT_MAX_WAIT_TIME_MS,
        maxQueueSize: Int = ReportSubmitter.DEFAULT_MAX_QUEUE_SIZE
    ) {
        mockStorage = mock()
        mockClient = mock()
        reporter = ReportSubmitter(
            mockStorage,
            mockClient,
            isNetworkAvailable,
            isFatal,
            initialWaitTimeMs,
            maxWaitTimeMs,
            maxQueueSize
        )
    }

    fun launchWorkerThread() {
        throwableQueue = ArrayBlockingQueue<Throwable?>(1)
        val queue = throwableQueue

        val thread = Thread({
            try {
                reporter.run()
                queue.add(WorkerStopped())
            }
            catch (t: Throwable) {
                queue.add(t)
            }
        })

        thread.isDaemon = true

        thread.start()
    }

    fun waitForWorkerThreadShutdown(sendShutdown: Boolean = true, timeout: Long = 200) {
        if (sendShutdown)
            sendShutdown()

        //XXX need to do something about spurious wake ups I guess
        val throwable = throwableQueue.poll(timeout, TimeUnit.MILLISECONDS)
        if (throwable != null) {
            if (throwable !is WorkerStopped)
                throw AssertionError("Worker thread failed", throwable)
        }
        else
            throw AssertionError("Worker didn't terminate within the given timeout")
    }

    @Test
    fun `should shutdown when receiving a Shutdown message`() {
        initReporter()

        processShutdown()

        assertTrue(reporter.isShutdown, "Reporter not in shutdown state")
    }

    @Test
    fun `should update network status on receiving a NetworkStatus message`() {
        initReporter()

        reporter.processMessage(ReporterMessage.NetworkStatus(true))

        assertTrue(reporter.isNetworkAvailable, "Network status not updated")
    }

    @Test
    fun `should queue reports while network is unavailable`() {
        initReporter()

        verifyNoMoreInteractions(mockClient)

        processReports(dummyReports)

        assertEquals(dummyReports.size, reporter.pendingReportCount, "Pending report count doesn't match")
    }

    @Test
    fun `should send queued reports when network comes back up`() {
        initReporter()

        processReports(dummyReports)

        whenever(mockClient.submit(any())).thenReturn(null)

        reporter.processMessage(ReporterMessage.NetworkStatus(true))

        verify(mockClient, times(2)).submit(any())
    }

    @Test
    fun `should enter fatal error mode upon receiving a fatal error`() {
        initReporter(isNetworkAvailable = true)

        whenever(mockClient.submit(any())).thenReturn(dummyFatalError)

        processReport(dummyReport)

        assertTrue(reporter.hasFatalErrorOccured, "Fatal error not recognized")
    }

    @Test
    fun `should stop submitting reports indefinitely once a fatal error is returned`() {
        initReporter()

        whenever(mockClient.submit(any())).thenReturn(dummyFatalError)

        processNetworkStatus(true)

        processReports(2)

        verify(mockClient, times(1)).submit(any())

        assertEquals(0, reporter.pendingReportCount, "Queued not cleared on fatal error")
    }

    @Test
    fun `should clear queued reports without any further processing when receiving a fatal error`() {
        initReporter()

        processReports(dummyReports)

        whenever(mockClient.submit(any())).thenReturn(dummyFatalError)

        processNetworkStatus(true)

        verify(mockClient, times(1)).submit(any())

        assertEquals(0, reporter.pendingReportCount, "Queued not cleared on fatal error")
    }

    @Test
    fun `should not longer queue received reports once a fatal error has been received`() {
        initReporter(isNetworkAvailable = true, isFatal = true)

        processReport(dummyReport)

        verifyZeroInteractions(mockClient)
    }

    @Test
    fun `should delay sending reports when receiving a recoverable error`() {
        val initialWaitTimeMs = 2000L
        initReporter(isNetworkAvailable = true, initialWaitTimeMs = initialWaitTimeMs)

        whenever(mockClient.submit(any())).thenReturn(dummyRecoverableError)

        val initialTime = 1000L
        withFixedMillis(initialTime) {
            processReport(dummyReport)
        }

        assertEquals(initialTime + initialWaitTimeMs, reporter.delayUntil, "Not setting a delay time after receiving a recoverable error")
    }

    @Test
    fun `should double the delay until max is reached when receiving increasing multiple recoverable errors`() {
        val initialWaitTimeMs = 2000L
        val maxWaitTimeMs = 10000L
        initReporter(isNetworkAvailable = true, initialWaitTimeMs = initialWaitTimeMs, maxWaitTimeMs = maxWaitTimeMs)

        whenever(mockClient.submit(any())).thenReturn(dummyRecoverableError)

        val initialTime = 1000L

        withFixedMillis(initialTime) {
            (0..4).forEach { i ->
                reporter.resetDelay()
                processReport(dummyReport)
                val expected = Math.min(initialWaitTimeMs shl i, maxWaitTimeMs)

                assertEquals(expected, reporter.currentWaitTimeMs, "Invalid delay")
            }
        }
    }

    @Test
    fun `successfully sending a report should reset the current wait time`() {
        val initialWaitTimeMs = 2000L
        initReporter(isNetworkAvailable = true, initialWaitTimeMs = initialWaitTimeMs)

        whenever(mockClient.submit(any()))
            .thenReturn(dummyRecoverableError)
            .thenReturn(null)

        processReports(2)

        assertEquals(initialWaitTimeMs, reporter.currentWaitTimeMs, "Current wait time not reset")
    }

    private fun withRecordingStores(body: () -> Unit): List<List<Int>>  {
        //for reasons beyond me, both ArgumentCaptor and inOrder are fucking broken here; it returns the final arg twice
        //only and I have no idea why; if I reverse the order then it acts like the first call was never made
        //so we just do this instead, which works fine
        val got = mutableListOf<List<Int>>()
        whenever(mockStorage.store(any())).thenAnswer { invocation ->
            got.add((invocation.arguments.first() as Collection<Int>).toList())
            Unit
        }

        body()

        return got
    }

    private fun testStorageWrite(isNetworkAvailable: Boolean, expected: List<List<Int>>) {
        initReporter(isNetworkAvailable = isNetworkAvailable)

        val reports = listOf(1, 2)

        val got = withRecordingStores {
            processReports(reports)
        }

        assertEquals(expected, got, "Invalid store calls")
    }

    @Test
    fun `should write out the contents of the queue while network is unavailable`() {
        val expected = listOf(
            listOf(1),
            listOf(1, 2)
        )

        testStorageWrite(false, expected)
    }

    @Test
    fun `it should write out the contents of the queue when network is available`() {
        //it'll write the report, then send it, then repeat
        val expected = listOf(
            listOf(1),
            emptyList(),
            listOf(2),
            emptyList()
        )

        testStorageWrite(true, expected)
    }

    @Test
    fun `should discard older reports when limit is reached and a new report comes in`() {
        val maxQueueSize = 2
        initReporter(maxQueueSize = maxQueueSize)

        val reports = (0..5)
        processReports(reports)

        assertEquals(2, reporter.pendingReportCount, "Invalid report size")

        assertEquals(listOf(4, 5), reporter.pendingReports, "Invalid reports")
    }

    @Test
    fun `should store reports after uploading is complete`() {
        initReporter(isNetworkAvailable = true)

        val got = withRecordingStores {
            processReport(0)
        }

        val expected = listOf(
            listOf(0),
            emptyList()
        )

        Assertions.assertThat(got).apply {
            describedAs("Should write out the empty queue after uploading is complete")
            containsExactlyElementsOf(expected)
        }
    }

    //threaded tests; make sure to use the send* helper variants, not process*

    @Test
    fun `should write the contents of the queue out while in delayed mode`() {
        initReporter(isNetworkAvailable = true)

        whenever(mockClient.submit(any())).thenReturn(dummyRecoverableError)

        launchWorkerThread()

        val got = withRecordingStores {
            sendReports(2)
            waitForWorkerThreadShutdown()
        }

        val expected = listOf(
            //write before sending
            listOf(1),
            //write after processing queue (aborted early due to error)
            listOf(1),
            //write after receiving another report while in delayed mode
            listOf(1, 2)
        )

        verify(mockClient, times(1)).submit(any())

        Assertions.assertThat(got).apply {
            describedAs("Should write reports to the queue while in delayed mode")
            containsExactlyElementsOf(expected)
        }
    }

    @Test
    fun `should read the contents of storage before running`() {
        initReporter()

        val reports = listOf(1, 2)

        whenever(mockStorage.get()).thenReturn(reports)

        launchWorkerThread()

        waitForWorkerThreadShutdown()

        verify(mockStorage, times(1)).get()
    }

    @Test
    fun `should send pending reports on startup if network is available`() {
        initReporter(isNetworkAvailable = true)

        val reports = listOf(1)

        whenever(mockStorage.get()).thenReturn(reports)

        whenever(mockClient.submit(any())).thenReturn(null)

        launchWorkerThread()

        waitForWorkerThreadShutdown()

        verify(mockClient).submit(1)
    }

    @Test
    fun `should discard older reports when the storage returns too many reports`() {
        val maxQueueSize = 2
        initReporter(maxQueueSize = maxQueueSize)

        val reports = (0..5).toList()

        whenever(mockStorage.get()).thenReturn(reports)

        launchWorkerThread()

        waitForWorkerThreadShutdown()

        assertEquals(listOf(4, 5), reporter.pendingReports, "Invalid reports")
    }

    //XXX this is kinda hacky to test
    @Test(timeout = 500)
    fun `should resume operation automatically after a recoverable error timeout occurs`() {
        initReporter(isNetworkAvailable = true, initialWaitTimeMs = 50)

        val lock = ReentrantLock()
        val cond = lock.newCondition()

        //used to avoid spurious wakeups
        val atomicInt = AtomicInteger(0)

        whenever(mockClient.submit(any()))
            .thenReturn(dummyRecoverableError)
            .thenAnswer { invocation ->
                atomicInt.set(1)
                lock.withLock {
                    cond.signalAll()
                }
                null
            }

        launchWorkerThread()

        sendReport(1)

        lock.withLock {
            while (atomicInt.get() == 0) {
                cond.await()
            }
        }

        waitForWorkerThreadShutdown()
    }
}
