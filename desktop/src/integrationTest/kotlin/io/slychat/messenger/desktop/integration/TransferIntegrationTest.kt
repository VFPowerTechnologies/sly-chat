package io.slychat.messenger.desktop.integration

import com.nhaarman.mockito_kotlin.MockitoKotlin
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.http.HttpClientConfig
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.JavaHttpClientFactory
import io.slychat.messenger.core.integration.utils.*
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.desktop.DesktopFileAccess
import io.slychat.messenger.services.StorageClientFactoryImpl
import io.slychat.messenger.services.UploadClientFactoryImpl
import io.slychat.messenger.services.UserPathsGenerator
import io.slychat.messenger.services.config.DummyConfigBackend
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.files.*
import io.slychat.messenger.testutils.*
import nl.komponents.kovenant.jvm.asDispatcher
import nl.komponents.kovenant.ui.KovenantUi
import org.assertj.core.api.Assertions
import org.junit.*
import rx.Observable
import rx.Scheduler
import rx.Subscription
import rx.schedulers.Schedulers
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.fail

class TransferIntegrationTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()

        init {
            MockitoKotlin.registerInstanceCreator { randomUpload() }

            //FIXME taken from services/test
            MockitoKotlin.registerInstanceCreator {
                val file = randomRemoteFile()
                val upload = randomUpload(file.id, file.remoteFileSize, UploadState.PENDING, null)
                UploadInfo(upload, file)
            }
        }

        @BeforeClass
        @JvmStatic
        fun beforeClass() {
            val devSettings = isDevFileServerRunning()

            require(devSettings.storageEnabled) { "Backend storage must be enabled" }
        }

        private val keyVault = generateNewKeyVault("test")
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())

    private fun getMockUploadPersistenceManager(): UploadPersistenceManager {
        val uploaderPersistenceManager: UploadPersistenceManager = mock()

        whenever(uploaderPersistenceManager.add(any())).thenResolveUnit()
        whenever(uploaderPersistenceManager.getAll()).thenResolve(emptyList())
        whenever(uploaderPersistenceManager.completePart(any(), any())).thenResolveUnit()
        whenever(uploaderPersistenceManager.setError(any(), any())).thenResolveUnit()
        whenever(uploaderPersistenceManager.setState(any(), any())).thenResolveUnit()

        return uploaderPersistenceManager
    }

    private fun getMockDownloadPersistenceManager(): DownloadPersistenceManager {
        val downloadPersistenceManager: DownloadPersistenceManager = mock()

        whenever(downloadPersistenceManager.add(any())).thenResolveUnit()
        whenever(downloadPersistenceManager.getAll()).thenResolve(emptyList())
        whenever(downloadPersistenceManager.setError(any(), any())).thenResolveUnit()
        whenever(downloadPersistenceManager.setState(any(), any())).thenResolveUnit()

        return downloadPersistenceManager
    }

    private fun getMockSyncJobFactory(): StorageSyncJobFactory {
        val syncJobFactory = mock<StorageSyncJobFactory>()
        val syncJob = mock<StorageSyncJob>()

        whenever(syncJob.run()).thenResolve(FileListSyncResult(0, FileListMergeResults.empty, 1, randomQuota()))
        whenever(syncJobFactory.create(any())).thenReturn(syncJob)

        return syncJobFactory
    }

    private val accountDir = File(System.getProperty("java.io.tmpdir")) / "sly-core-integration-tests-${randomString(5)}"
    private lateinit var executor: ExecutorService
    private lateinit var mainScheduler: Scheduler

    @Before
    fun before() {
        devClient.clear()

        accountDir.mkdirs()

        executor = Executors.newSingleThreadExecutor(object : ThreadFactory {
            private val defaultFactory = Executors.defaultThreadFactory()

            override fun newThread(r: Runnable): Thread {
                val thread = defaultFactory.newThread(r)
                thread.name = "Main Thread"
                thread.isDaemon = true
                return thread
            }
        })

        KovenantUi.uiContext {
            dispatcher = executor.asDispatcher()
        }

        mainScheduler = Schedulers.from(executor)
    }

    @After
    fun after() {
        accountDir.deleteRecursively()

        executor.shutdownNow()
    }

    private fun failWithExceptionInfo(message: String, e: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        pw.flush()

        fail("$message: ${e.message}\n$sw")
    }

    private inline fun <reified T : Transfer, R> getTransferErrorObservable(storageService: StorageService): Observable<R> {
        return storageService.transferEvents
            .ofType(TransferEvent.StateChanged::class.java)
            .filter { it.transfer is T && it.state == TransferState.ERROR }
            .flatMap {
                Observable.error<R>(RuntimeException("Transfer error: ${it.transfer.error}"))
            }
    }

    private fun <R> Subscription.use(body: () -> R): R {
        return try {
            body()
        }
        finally {
            unsubscribe()
        }
    }

    //TODO need to do something about the timeout... so long as we have periodic TransferEvent.Progress coming in we don't need to timeout
    //it's mostly here to make sure the transfer actually begins
    private fun uploadFile(inputFile: File, storageService: StorageService): RemoteFile {
        var f: RemoteFile? = null

        val errorObservable = getTransferErrorObservable<Transfer.U, RemoteFile>(storageService)

        val okObservable = storageService.transferEvents
            .ofType(TransferEvent.StateChanged::class.java)
            .filter { it.transfer.isUpload && it.state == TransferState.COMPLETE }
            .map { ev ->
                val status = storageService.transfers.find { it.id == ev.transfer.id }!!
                status.file!!
            }

        val resultObservable = Observable.merge(errorObservable, okObservable)
            .first()
            .timeout(50, TimeUnit.SECONDS)
            .replay()

        resultObservable.connect().use {
            storageService.uploadFile(
                inputFile.path,
                "/testing",
                "testing.txt",
                false
            ).get()

            resultObservable
                .toBlocking()
                .subscribe(
                    { f = it },
                    { failWithExceptionInfo("An error occured during upload", it) }
                )
        }

        return f!!
    }

    private fun downloadFile(file: RemoteFile, outputFile: File, storageService: StorageService) {
        val errorObservable = getTransferErrorObservable<Transfer.D, TransferEvent>(storageService)

        val okObservable = storageService.transferEvents
            .ofType(TransferEvent.StateChanged::class.java)
            .filter { it.transfer is Transfer.D && it.state == TransferState.COMPLETE }

        val resultObservable = Observable.merge(errorObservable, okObservable)
            .first()
            .timeout(50, TimeUnit.SECONDS)
            .replay()

        resultObservable.connect().use {
            storageService.downloadFiles(
                file.id,
                outputFile.path
            ).get()

            resultObservable
                .toBlocking()
                .subscribe(
                    { },
                    { failWithExceptionInfo("An error occured during download", it) }
                )
        }
    }

    private fun assertFilesEqual(inputFile: File, outputFile: File) {
        val totalSize = inputFile.length()
        assertEquals(totalSize, outputFile.length(), "Output file size differs from input file size")

        inputFile.inputStream().use { inputStream ->
            outputFile.inputStream().use { outputStream ->
                val inputBuffer = ByteArray(8 * 1024)
                val outputBuffer = ByteArray(8 * 1024)
                var totalRead = 0L

                while (true) {
                    val inputRead = inputStream.read(inputBuffer)
                    val outputRead = outputStream.read(outputBuffer)

                    assertEquals(inputRead, outputRead, "inputRead is not the same as outputRead")

                    if (inputRead == -1)
                        break

                    val inCmp = if (inputRead == inputBuffer.size)
                        inputBuffer
                    else
                        Arrays.copyOf(inputBuffer, inputRead)

                    val outCmp = if (outputRead == outputBuffer.size)
                        outputBuffer
                    else
                        Arrays.copyOf(outputBuffer, outputRead)

                    Assertions.assertThat(outCmp).desc("Offset $totalRead should be equal") {
                        inHexadecimal()
                        isEqualTo(inCmp)
                    }

                    totalRead += inputRead
                }

                assertEquals(totalSize, totalRead, "Read size doesn't equal file size")
            }
        }
    }

    private fun testFileTransfer(fileSize: Long) {
        val userManagement = SiteUserManagement(devClient)
        val authUser = devClient.newAuthUser(userManagement)

        val uploaderPersistenceManager = getMockUploadPersistenceManager()

        val downloadPersistenceManager = getMockDownloadPersistenceManager()

        val fileAccess = DesktopFileAccess()

        val authTokenManager = MockAuthTokenManager(authUser.userCredentials)

        val httpClientFactory = JavaHttpClientFactory(HttpClientConfig(4000, 4000), null)
        val uploadClientFactory = UploadClientFactoryImpl(serverBaseUrl, fileServerBaseUrl, httpClientFactory)

        val uploadOperations = UploadOperationsImpl(
            fileAccess,
            authTokenManager,
            uploadClientFactory,
            keyVault,
            Schedulers.io()
        )

        val uploader = UploaderImpl(
            1,
            uploaderPersistenceManager,
            uploadOperations,
            Schedulers.computation(),
            mainScheduler,
            true
        )

        val storageclientFactory = StorageClientFactoryImpl(serverBaseUrl, fileServerBaseUrl, httpClientFactory)
        val downloadOperations = DownloadOperationsImpl(
            fileAccess,
            authTokenManager,
            storageclientFactory,
            Schedulers.io()
        )

        val downloader = DownloaderImpl(
            1,
            downloadPersistenceManager,
            downloadOperations,
            Schedulers.computation(),
            mainScheduler,
            true
        )

        val userConfigService = UserConfigService(DummyConfigBackend())

        val networkStatus = Observable.just(true)

        val transferManager = TransferManagerImpl(
            userConfigService,
            uploader,
            downloader,
            mainScheduler,
            Schedulers.computation(),
            networkStatus
        )

        val userId = UserId(1)

        val userPaths = UserPathsGenerator(object : PlatformInfo {
            override val appFileStorageDirectory: File
                get() = accountDir
        }).getPaths(userId)

        val fileListPersistenceManager = mock<FileListPersistenceManager>()

        val storageService = StorageServiceImpl(
            authTokenManager,
            fileListPersistenceManager,
            getMockSyncJobFactory(),
            transferManager,
            fileAccess,
            networkStatus
        )

        try {
            withTempFile { inputFile ->
                //cheap way to preallocate an empty file
                //even if this isn't zero'ed we don't care for our purposes
                RandomAccessFile(inputFile.path, "rw").use {
                    it.setLength(fileSize)
                }

                val file = uploadFile(inputFile, storageService)

                whenever(fileListPersistenceManager.getFile(file.id)).thenResolve(file)

                withTempFile { outputFile ->
                    downloadFile(file, outputFile, storageService)

                    assertFilesEqual(inputFile, outputFile)
                }
            }
        }
        finally {
            storageService.shutdown()
        }
    }

    @Test
    fun `it should be able to upload and then download a single part upload file`() {
        testFileTransfer(10)
    }

    @Test
    fun `it should be able to upload and then download a multipart part upload file`() {
        testFileTransfer(5.mb + 1L)
    }
}