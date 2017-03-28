package io.slychat.messenger.core.integration.web

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.JavaHttpClient
import io.slychat.messenger.core.http.api.offline.OfflineMessagesClearRequest
import io.slychat.messenger.core.http.api.offline.OfflineMessagesClient
import io.slychat.messenger.core.http.api.offline.SerializedOfflineMessage
import io.slychat.messenger.core.integration.utils.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test

class WebApiOfflineMessagesTest {
    companion object {
        @ClassRule
        @JvmField
        val isDevServerRunning = IsDevServerRunningClassRule()
    }

    private val devClient = DevClient(serverBaseUrl, JavaHttpClient())
    private val userManagement = SiteUserManagement(devClient)

    private lateinit var fromUser: GeneratedSiteUser
    private lateinit var fromUserId: UserId
    private var fromDeviceId: Int = 0

    private lateinit var toUser: GeneratedSiteUser
    private lateinit var toUserId: UserId
    private var toDeviceId: Int = 0

    private var currentTimestamp = 1L

    @Before
    fun before() {
        devClient.clear()

        currentTimestamp = 1

        fromUser = userManagement.injectNamedSiteUser("a@a.com")
        fromUserId = fromUser.user.id
        fromDeviceId = devClient.addDevice(fromUser.user.email, io.slychat.messenger.core.integration.utils.defaultRegistrationId, DeviceState.ACTIVE)

        toUser = userManagement.injectNamedSiteUser("b@a.com")
        toUserId = toUser.user.id
        toDeviceId = devClient.addDevice(toUser.user.email, io.slychat.messenger.core.integration.utils.defaultRegistrationId, DeviceState.ACTIVE)
    }

    private fun insertMessages(n: Int = 3): List<SerializedOfflineMessage> {
        val offlineMessages = (currentTimestamp..currentTimestamp + (n - 1)).map {
            SerializedOfflineMessage(SlyAddress(fromUserId, fromDeviceId), it, it.toString())
        }

        currentTimestamp += n

        devClient.addOfflineMessages(toUserId, toDeviceId, offlineMessages)

        return offlineMessages
    }

    @Test
    fun `should return all stored offline messages`() {
        val offlineMessages = insertMessages()

        val client = OfflineMessagesClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(toUser.user.email)

        val receivedOfflineMessages = client.get(toUser.getUserCredentials(authToken))

        assertThat(receivedOfflineMessages.messages).apply {
            describedAs("Should return the stored offline messages")
            containsExactlyElementsOf(offlineMessages)
        }
    }

    @Test
    fun `deletion should only remove the specified range`() {
        val client = OfflineMessagesClient(io.slychat.messenger.core.integration.utils.serverBaseUrl, JavaHttpClient())

        val authToken = devClient.createAuthToken(toUser.user.email)
        val userCredentials = toUser.getUserCredentials(authToken)

        insertMessages()

        val receivedBefore = client.get(userCredentials)

        assertThat(receivedBefore.messages).apply {
            describedAs("Should return stored messages")
            isNotEmpty()
        }

        val afterMessages = insertMessages()

        client.clear(userCredentials, OfflineMessagesClearRequest(receivedBefore.range))

        val receivedAfter = client.get(userCredentials)

        assertThat(receivedAfter.messages).apply {
            describedAs("Should only clear the specified range")
            containsExactlyElementsOf(afterMessages)
        }
    }
}