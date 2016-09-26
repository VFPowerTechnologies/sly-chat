package io.slychat.messenger.desktop

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.randomConversationDisplayInfo
import io.slychat.messenger.services.NotificationConversationInfo
import io.slychat.messenger.services.NotificationState
import io.slychat.messenger.services.config.DummyConfigBackend
import io.slychat.messenger.services.config.SoundFilePath
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.di.UserComponent
import javafx.scene.media.AudioClip
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject
import java.io.File
import kotlin.test.assertEquals

class DesktopNotificationServiceTest {
    companion object {
        val dummyAudioUri: String = javaClass.getResource("/silence.wav").toExternalForm()
        val dummyAudio: AudioClip

        init {
            dummyAudio = AudioClip(dummyAudioUri)

            MockitoKotlin.registerInstanceCreator { dummyAudio }
        }
    }

    private val userConfigService = UserConfigService(DummyConfigBackend())
    private val audioPlayback: AudioPlayback = mock()
    private val notificationDisplay: NotificationDisplay = mock()
    private val userComponent: UserComponent = mock()
    private val userSessionAvailable: PublishSubject<UserComponent?> = PublishSubject.create()

    private fun useDummyAudio() {
        userConfigService.withEditor {
            notificationsSound = SoundFilePath("Dummy", dummyAudioUri)
        }
    }

    @Before
    fun before() {
        whenever(userComponent.userConfigService).thenReturn(userConfigService)
    }

    private fun getNotificationState(hasNew: Boolean): NotificationState {
        return NotificationState(
            listOf(NotificationConversationInfo(randomConversationDisplayInfo(), hasNew))
        )
    }

    private fun createNotificationService(doInit: Boolean = false): DesktopNotificationService {
        val notificationService = DesktopNotificationService(audioPlayback, notificationDisplay)

        if (doInit)
            notificationService.init(userSessionAvailable)

        return notificationService
    }

    @Test
    fun `it should default to silence if not logged in`() {
        val notificationService = createNotificationService()

        val notificationState = getNotificationState(true)

        notificationService.updateNotificationState(notificationState)

        verify(audioPlayback, never()).play(any())
    }

    @Test
    fun `it should refresh its notification sound cache when the associated setting is modified`() {
        val notificationService = createNotificationService(doInit = true)

        userSessionAvailable.onNext(userComponent)

        useDummyAudio()

        notificationService.updateNotificationState(getNotificationState(true))

        verify(audioPlayback).play(capture {
            assertEquals(dummyAudioUri, it.source, "Invalid audio URI")
        })
    }

    @Test
    fun `it should refresh its cache on session creation`() {
        useDummyAudio()

        val notificationService = createNotificationService(doInit = true)

        userSessionAvailable.onNext(userComponent)

        notificationService.updateNotificationState(getNotificationState(true))

        verify(audioPlayback).play(capture {
            assertEquals(dummyAudioUri, it.source, "Invalid audio URI")
        })
    }

    @Test
    fun `it should clear its cache on session destruction`() {
        val notificationService = createNotificationService(doInit = true)

        userSessionAvailable.onNext(userComponent)
        userSessionAvailable.onNext(null)

        notificationService.updateNotificationState(getNotificationState(true))

        verify(audioPlayback, never()).play(any())
    }

    @Test
    fun `it should use silence if the notification sound setting is an invalid path`() {
        val notificationService = createNotificationService(doInit = true)

        userConfigService.withEditor {
            notificationsSound = SoundFilePath("bad", "bad")
        }

        userSessionAvailable.onNext(userComponent)

        notificationService.updateNotificationState(getNotificationState(true))

        verify(audioPlayback, never()).play(any())
    }

    @Test
    fun `it should use silence if the notification sound setting is a non-existent path`() {
        val notificationService = createNotificationService(doInit = true)

        userConfigService.withEditor {
            val noSuchFile = File(System.getProperty("java.io.tmpdir"), randomUUID())

            notificationsSound = SoundFilePath("missing", noSuchFile.toString())
        }

        userSessionAvailable.onNext(userComponent)

        notificationService.updateNotificationState(getNotificationState(true))

        verify(audioPlayback, never()).play(any())
    }

    @Test
    fun `it should trigger a notification for entries with hasNew=true`() {
        val notificationService = createNotificationService(doInit = true)

        notificationService.updateNotificationState(getNotificationState(true))

        verify(notificationDisplay).displayNotification(any(), any())
    }

    @Test
    fun `it should not trigger a notification for entries with hasNew=false`() {
        val notificationService = createNotificationService(doInit = true)

        notificationService.updateNotificationState(getNotificationState(false))

        verify(notificationDisplay, never()).displayNotification(any(), any())
    }
}