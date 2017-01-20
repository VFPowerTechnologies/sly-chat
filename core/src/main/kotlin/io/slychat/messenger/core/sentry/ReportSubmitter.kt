package io.slychat.messenger.core.sentry

import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Handles storing and submitting reports.
 *
 * Once shutdown, will not start up again; run will throw an IllegalStateException if called.
 */
class ReportSubmitter<ReportType>(
    private val storage: ReportStorage<ReportType>,
    private val client: ReportSubmitClient<ReportType>,
    initialNetworkStatus: Boolean = false,
    initialFatalStatus: Boolean = false,
    private val initialWaitTimeMs: Long = DEFAULT_INITIAL_WAIT_TIME_MS,
    private val maxWaitTimeMs: Long = DEFAULT_MAX_WAIT_TIME_MS,
    val maxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE
) : ReportSubmitterCommunicator<ReportType> {
    companion object {
        val DEFAULT_INITIAL_WAIT_TIME_MS = TimeUnit.SECONDS.toMillis(10)
        val DEFAULT_MAX_WAIT_TIME_MS = TimeUnit.MINUTES.toMillis(5)
        val DEFAULT_MAX_QUEUE_SIZE = 10

        private fun currentTimeMs(): Long = DateTime().millis
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val messageQueue = ArrayBlockingQueue<ReporterMessage<ReportType>>(10)
    private val queuedReports = ArrayDeque<ReportType>(maxQueueSize)

    private val shutdownDeferred = deferred<Unit, Exception>()
    //will never be rejected
    override val shutdownPromise: Promise<Unit, Exception>
        get() = shutdownDeferred.promise

    //if this is non-zero, don't attempt to submit any messages until the current time exceeds this value
    var delayUntil = 0L
        private set

    //how many seconds to wait until the next attempt when a recoverable error occurs
    var currentWaitTimeMs: Long = 0
        private set

    val pendingReportCount: Int
        get() = queuedReports.size

    var isNetworkAvailable = initialNetworkStatus
        private set

    private var shutdown = false
    val isShutdown: Boolean
        get() = shutdown

    var hasFatalErrorOccured = initialFatalStatus
        private set

    private fun storeReports() {
        try {
            storage.store(queuedReports)
        }
        catch (t: Throwable) {
            log.error("Unable to store reports: {}", t, t.message)
        }
    }

    private fun getReports() {
        try {
            val elements = storage.get()
            log.info("Read {} reports from storage", elements.size)
            queuedReports.addAll(elements)
            trimQueue()
        }
        catch (t: Throwable) {
            log.error("Unable to get reports: {}", t, t.message)
        }
    }

    //only used for testing
    internal fun resetDelay() {
        delayUntil = 0
    }

    //read from queue/etc
    fun run() {
        if (isShutdown)
            error("Already shutdown")

        getReports()
        submitReports(true)

        while (!shutdown) {
            val message = if (delayUntil != 0L) {
                val current = currentTimeMs()

                val waitTime = delayUntil - current
                if (waitTime <= 0) {
                    delayUntil = 0
                    messageQueue.take()
                }
                else
                    messageQueue.poll(waitTime, TimeUnit.MILLISECONDS)
            }
            else
                messageQueue.take()

            //could be a timeout, or a spurious wakeup
            if (message == null) {
                val current = currentTimeMs()
                //timeout, so attempt to resume sending reports
                if (current >= delayUntil) {
                    delayUntil = 0
                    submitReports()
                }

                continue
            }

            processMessage(message)
        }

        shutdownDeferred.resolve(Unit)
    }

    /** Removes the oldest reports in the queue until it drops back to the max size. */
    private fun trimQueue() {
        while (queuedReports.size > maxQueueSize)
            queuedReports.pop()
    }

    internal fun processMessage(message: ReporterMessage<ReportType>) {
        when (message) {
            is ReporterMessage.Shutdown ->
                shutdown = true

            is ReporterMessage.BugReport -> {
                if (!hasFatalErrorOccured) {
                    queuedReports.add(message.report)
                    trimQueue()
                    submitReports()
                }
            }

            is ReporterMessage.NetworkStatus -> {
                if (!hasFatalErrorOccured) {
                    isNetworkAvailable = message.isAvailable
                    if (isNetworkAvailable)
                        submitReports()
                }
            }
        }
    }

    private fun delayProcessing() {
        currentWaitTimeMs = if (currentWaitTimeMs == 0L)
            initialWaitTimeMs
        else {
            val newWaitTime = Math.min(currentWaitTimeMs * 2, maxWaitTimeMs)
            currentWaitTimeMs = newWaitTime
            newWaitTime
        }

        delayUntil = currentTimeMs() + currentWaitTimeMs
    }

    private fun submitReports(noWrite: Boolean = false) {
        if (queuedReports.isEmpty())
            return

        //just a hack for the initial run so we don't pointlessly rewrite the reports we just read
        if (!noWrite)
            storeReports()

        if (!isNetworkAvailable || delayUntil != 0L)
            return

        while (queuedReports.isNotEmpty()) {
            val next = queuedReports.peek()

            val error = try {
                client.submit(next)
            }
            catch (t: Throwable) {
                //this'll log an error below, so we just put a warning here about the misbehaving client
                log.warn("ReportSubmitClient client threw an exception; treating as fatal error")

                ReportSubmitError.Fatal(t.message ?: "no message", t)
            }

            if (error != null) {
                when (error) {
                    is ReportSubmitError.Recoverable -> {
                        log.warn("Unable to submit report: {}", error.message, error.cause)
                        delayProcessing()
                    }

                    is ReportSubmitError.Fatal -> {
                        //this'll end up in the queue, so it could technically be submitted at some point (app
                        //restart/etc), depending on what the fatal error's cause was
                        log.error("A fatal error occured: {}", error.message, error.cause)
                        hasFatalErrorOccured = true
                        queuedReports.clear()
                    }
                }

                break
            }
            else {
                currentWaitTimeMs = initialWaitTimeMs
                queuedReports.pop()
            }
        }

        storeReports()
    }

    internal val pendingReports: Collection<ReportType>
        get() = queuedReports.toList()

    override fun shutdown() {
        messageQueue.offer(ReporterMessage.Shutdown())
    }

    override fun updateNetworkStatus(isAvailable: Boolean) {
        messageQueue.offer(ReporterMessage.NetworkStatus(isAvailable))
    }

    override fun submit(report: ReportType) {
        messageQueue.offer(ReporterMessage.BugReport(report))
    }
}