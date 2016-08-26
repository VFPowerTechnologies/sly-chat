package io.slychat.messenger.services.contacts

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.http.api.ResourceConflictException
import io.slychat.messenger.core.http.api.contacts.*
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.PlatformContacts
import io.slychat.messenger.services.UserData
import io.slychat.messenger.services.crypto.MockAuthTokenManager
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenAnswerSuccess
import io.slychat.messenger.testutils.thenReturn
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddressBookSyncJobImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        val keyVault = generateNewKeyVault("test")
    }

    val emptyMd5 = "d41d8cd98f00b204e9800998ecf8427e"
    val updateMd5 = "d41d8cd98f00b204e9800998ecf8427f"

    val contactAsyncClient: ContactAsyncClient = mock()
    val addressBookAsyncClient: AddressBookAsyncClient = mock()
    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val groupPersistenceManager:  GroupPersistenceManager = mock()
    val userLoginData = UserData(SlyAddress(randomUserId(), 1), keyVault)
    val accountRegionCode = "1"
    val platformContacts: PlatformContacts = mock()
    val timerFactory: TimerFactory = mock()

    @Before
    fun before() {
        whenever(platformContacts.fetchContacts()).thenReturn(emptyList())

        whenever(contactsPersistenceManager.findMissing(any())).thenReturn(listOf())
        whenever(contactsPersistenceManager.add(any<Collection<ContactInfo>>())).thenReturn(emptySet())
        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(emptyList())
        whenever(contactsPersistenceManager.removeRemoteUpdates(any())).thenReturn(Unit)
        whenever(contactsPersistenceManager.applyDiff(any(), any())).thenReturn(Unit)
        whenever(contactsPersistenceManager.exists(anySet())).thenAnswerSuccess {
            val a = it.arguments[0]
            @Suppress("UNCHECKED_CAST")
            (a as Set<UserId>)
        }

        whenever(groupPersistenceManager.applyDiff(any())).thenReturn(Unit)
        whenever(groupPersistenceManager.getRemoteUpdates()).thenReturn(emptyList())
        whenever(groupPersistenceManager.removeRemoteUpdates(any())).thenReturn(Unit)

        whenever(contactAsyncClient.findLocalContacts(any(), any())).thenReturn(FindLocalContactsResponse(emptyList()))
        whenever(contactAsyncClient.fetchContactInfoById(any(), any())).thenReturn(FetchContactInfoByIdResponse(emptyList()))

        whenever(contactsPersistenceManager.getAddressBookHash()).thenReturn(emptyMd5)
        whenever(contactsPersistenceManager.addRemoteEntryHashes(any())).thenReturn(emptyMd5)

        whenever(addressBookAsyncClient.update(any(), any())).thenReturn(UpdateAddressBookResponse(updateMd5))
        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(emptyList()))

        whenever(timerFactory.run(any(), any())).thenReturn(Unit)
    }

    fun newJob(): AddressBookSyncJobImpl {
        return AddressBookSyncJobImpl(
            MockAuthTokenManager(),
            contactAsyncClient,
            addressBookAsyncClient,
            contactsPersistenceManager,
            groupPersistenceManager,
            userLoginData,
            accountRegionCode,
            platformContacts,
            timerFactory
        )
    }

    fun runJobWithDescription(body: AddressBookSyncJobDescription.() -> Unit): AddressBookSyncResult {
        val syncJob = newJob()

        val description = AddressBookSyncJobDescription()
        description.body()

        return syncJob.run(description).get()
    }

    fun runPush(): AddressBookSyncResult {
        return runJobWithDescription { doPush() }
    }

    fun runFindPlatformContacts(): AddressBookSyncResult {
        return runJobWithDescription { doFindPlatformContacts() }
    }

    fun runPull(): AddressBookSyncResult {
        return runJobWithDescription { doPull() }
    }

    fun randomRemoteEntries(): List<RemoteAddressBookEntry> {
        val missing = randomUserIds()
        return encryptRemoteAddressBookEntries(keyVault, missing.map { AddressBookUpdate.Contact(it, AllowedMessageLevel.ALL) })
    }

    @Test
    fun `a pull should fetch any missing contact info`() {
        val missing = randomUserIds()
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, missing.map { AddressBookUpdate.Contact(it, AllowedMessageLevel.ALL) })

        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(remoteEntries))
        whenever(contactsPersistenceManager.exists(missing)).thenReturn(emptySet())

        runPull()

        verify(contactAsyncClient).fetchContactInfoById(any(), capture {
            assertThat(it.ids).apply {
                `as`("Missing ids should be looked up")
                containsOnlyElementsOf(missing)
            }
        })
    }

    @Test
    fun `a pull should add missing contacts with the proper message levels`() {
        val missing = randomUserIds(3)
        val messageLevels = missing.zip(listOf(
            AllowedMessageLevel.ALL,
            AllowedMessageLevel.BLOCKED,
            AllowedMessageLevel.GROUP_ONLY
        )).toMap()

        val apiContacts = missing.map { ApiContactInfo(it, "$it@a.com", it.toString(), it.toString(), it.toString()) }
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, missing.map { AddressBookUpdate.Contact(it, messageLevels[it]!!) })

        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(remoteEntries))
        whenever(contactsPersistenceManager.exists(missing)).thenReturn(emptySet())
        whenever(contactAsyncClient.fetchContactInfoById(any(), any())).thenReturn(FetchContactInfoByIdResponse(apiContacts))

        runPull()

        verify(contactsPersistenceManager).applyDiff(capture {
            assertEquals(missing.size, it.size, "New contacts size doesn't match")

            it.forEach {
                assertEquals(messageLevels[it.id]!!, it.allowedMessageLevel, "Invalid message level")
            }
        }, any())
    }

    @Test
    fun `a pull should update existing contacts with the proper message level`() {
        val present = randomUserIds(3)
        val messageLevels = listOf(AllowedMessageLevel.ALL, AllowedMessageLevel.GROUP_ONLY, AllowedMessageLevel.BLOCKED)
        val remoteUpdates = present.zip(messageLevels).map { AddressBookUpdate.Contact(it.first, it.second) }
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, remoteUpdates)

        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(remoteEntries))
        whenever(contactsPersistenceManager.exists(present)).thenReturn(present)

        runPull()

        verify(contactsPersistenceManager).applyDiff(any(), capture {
            assertThat(it).apply {
                `as`("Existing contacts should have their message levels updated")
                containsOnlyElementsOf(remoteUpdates)
            }
        })
    }

    @Test
    fun `a pull not should add contacts if none are present`() {
        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(emptyList()))

        runPull()

        verify(contactsPersistenceManager, never()).applyDiff(any(), any())
    }

    @Test
    fun `a pull should indicate a full pull was done if entries are returned from the server`() {
        val update = AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        val entries = encryptRemoteAddressBookEntries(keyVault, listOf(update))

        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(entries))

        val result = runPull()

        assertTrue(result.fullPull, "Indicates a full pull wasn't done")
    }

    @Test
    fun `a pull should indicate no full pull was done if no entries are returned from the server`() {
        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(emptyList()))

        val result = runPull()

        assertFalse(result.fullPull, "Indicates a full pull was done")
    }

    @Test
    fun `a pull should update the address book hashes`() {
        val update = AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        val entries = encryptRemoteAddressBookEntries(keyVault, listOf(update))

        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(entries))

        runPull()

        verify(contactsPersistenceManager).addRemoteEntryHashes(entries)
    }

    @Test
    fun `a pull should add contacts before groups`() {
        val groupInfo = randomGroupInfo()

        val userId = randomUserId()
        val apiContacts = listOf(ApiContactInfo(userId, "$userId@a.com", userId.toString(), userId.toString(), userId.toString()))
        val remoteUpdates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), GroupMembershipLevel.JOINED),
            AddressBookUpdate.Contact(userId, AllowedMessageLevel.ALL)
        )

        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, remoteUpdates)

        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(remoteEntries))
        whenever(contactAsyncClient.fetchContactInfoById(any(), any())).thenReturn(FetchContactInfoByIdResponse(apiContacts))

        runPull()

        val order = inOrder(contactsPersistenceManager, groupPersistenceManager)
        order.verify(contactsPersistenceManager).applyDiff(any(), any())
        order.verify(groupPersistenceManager).applyDiff(any())
    }

    @Test
    fun `a pull should add missing groups`() {
        val groupInfo = randomGroupInfo()

        val remoteUpdates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), GroupMembershipLevel.JOINED)
        )
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, remoteUpdates)

        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(remoteEntries))

        runPull()

        verify(groupPersistenceManager).applyDiff(remoteUpdates)
    }

    @Test
    fun `a pull not should add groups if none are present`() {
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, emptyList())

        whenever(addressBookAsyncClient.get(any(), any())).thenReturn(GetAddressBookResponse(remoteEntries))

        runPull()

        verify(groupPersistenceManager, never()).applyDiff(any())
    }

    @Test
    fun `a push should not issue a remote request if no missing platform contacts are found`() {
        runPush()

        verify(addressBookAsyncClient, never()).update(any(), any())
    }

    @Test
    fun `a push should indicate no updates were performed if no updates are present`() {
        val result = runPush()

        assertEquals(0, result.updateCount, "Update count wasn't 0")
    }

    @Test
    fun `a push should indicate no updates were performed if the server returns the same version number`() {
        val groupInfo = randomGroupInfo()

        val groupUpdates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), groupInfo.membershipLevel)
        )

        val contactUpdates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(contactUpdates)
        whenever(groupPersistenceManager.getRemoteUpdates()).thenReturn(groupUpdates)

        whenever(contactsPersistenceManager.getAddressBookHash()).thenReturn(emptyMd5)
        whenever(addressBookAsyncClient.update(any(), any())).thenReturn(UpdateAddressBookResponse(emptyMd5))

        val result = runPush()

        assertEquals(0, result.updateCount, "Update count wasn't 0")
    }

    @Test
    fun `a push should indicate the number of updates available if the server returns a new version number`() {
        val groupInfo = randomGroupInfo()

        val groupUpdates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), groupInfo.membershipLevel)
        )

        val contactUpdates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(contactUpdates)
        whenever(groupPersistenceManager.getRemoteUpdates()).thenReturn(groupUpdates)

        val result = runPush()

        assertEquals(2, result.updateCount, "Update count is incorrect")
    }

    @Test
    fun `a push should send group updates`() {
        val groupInfo = randomGroupInfo()

        val updates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), groupInfo.membershipLevel)
        )

        whenever(groupPersistenceManager.getRemoteUpdates()).thenReturn(updates)

        runPush()

        val request = updateRequestFromAddressBookUpdates(keyVault, updates)
        verify(addressBookAsyncClient).update(any(), eq(request))
    }

    @Test
    fun `a push should send contact updates`() {
        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(updates)

        runPush()

        val request = updateRequestFromAddressBookUpdates(keyVault, updates)
        verify(addressBookAsyncClient).update(any(), eq(request))
    }

    @Test
    fun `a push should delete group updates after a successful update`() {
        val groupInfo = randomGroupInfo()

        val updates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), groupInfo.membershipLevel)
        )

        whenever(groupPersistenceManager.getRemoteUpdates()).thenReturn(updates)

        runPush()

        verify(groupPersistenceManager).removeRemoteUpdates(updates.map { it.groupId })
    }

    @Test
    fun `a push should delete contact updates after a successful update`() {
        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(updates)

        runPush()

        verify(contactsPersistenceManager).removeRemoteUpdates(updates.map { it.userId })
    }

    @Test
    fun `a push should update the address book hashes`() {
        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, updates)

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(updates)

        runPush()

        verify(contactsPersistenceManager).addRemoteEntryHashes(remoteEntries)
    }

    @Test
    fun `a push should retry up to MAX_RETRIES times and finally rethrow the exception when receiving a ResourceConflictException`() {
        val retries = AddressBookSyncJobImpl.UPDATE_MAX_RETRIES
        val totalAttempts = retries + 1

        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(updates)

        var ongoing = whenever(addressBookAsyncClient.update(any(), any()))
            .thenReturn(ResourceConflictException())

        (1..retries).forEach {
            ongoing = ongoing.thenReturn(ResourceConflictException())
        }

        assertFailsWith(ResourceConflictException::class) {
            runPush()
        }

        verify(addressBookAsyncClient, times(totalAttempts)).update(any(), any())
    }

    @Test
    fun `a pull should not issue a remote request if no contacts need to be added`() {
        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(emptyList())

        runPull()

        verify(contactAsyncClient, never()).fetchContactInfoById(any(), any())
    }

    @Test
    fun `a pull request should include the current address book hash`() {
        whenever(contactsPersistenceManager.getAddressBookHash()).thenReturn(emptyMd5)

        runPull()

        verify(contactsPersistenceManager).getAddressBookHash()
        verify(addressBookAsyncClient).get(any(), eq(GetAddressBookRequest(emptyMd5)))
    }

    @Test
    fun `a find platform contacts should not issue a remote request if no platform contacts are found`() {
        whenever(platformContacts.fetchContacts()).thenReturn(emptyList())

        runFindPlatformContacts()

        verify(contactAsyncClient, never()).findLocalContacts(any(), any())
    }

    @Test
    fun `a find platform contacts should not issue a remote request if no missing local contacts are found`() {
        val platformContact = PlatformContact("name", listOf("a@a.com"), listOf("15555555555"))
        whenever(platformContacts.fetchContacts()).thenReturn(listOf(platformContact))
        whenever(contactsPersistenceManager.findMissing(anyList())).thenReturn(emptyList())

        runFindPlatformContacts()

        verify(contactAsyncClient, never()).findLocalContacts(any(), any())
    }

    @Test
    fun `a find platform contacts should query for new contacts using missing local platform contact data`() {
        val platformContact = PlatformContact("name", listOf("a@a.com"), listOf("15555555555"))
        val missingContacts = listOf(platformContact)

        whenever(platformContacts.fetchContacts()).thenReturn(missingContacts)
        whenever(contactsPersistenceManager.findMissing(anyList())).thenReturn(missingContacts)

        runFindPlatformContacts()

        verify(contactAsyncClient).findLocalContacts(any(), eq(FindLocalContactsRequest(missingContacts)))
    }

    @Test
    fun `a find platform contacts should add local contacts with remote accounts to the contact list with ALL message level`() {
        val userId = randomUserId()
        val email = "a@a.com"
        val name = "name"
        val phoneNumber = "15555555555"
        val publicKey = "pubkey"
        val platformContact = PlatformContact(name, listOf(email), listOf(phoneNumber))

        val missingContacts = listOf(platformContact)
        val apiContacts =  listOf(
            ApiContactInfo(userId, email, name, phoneNumber, publicKey)
        )
        val contactInfo = ContactInfo(userId, email, name, AllowedMessageLevel.ALL, phoneNumber, publicKey)

        whenever(platformContacts.fetchContacts()).thenReturn(missingContacts)
        whenever(contactsPersistenceManager.findMissing(anyList())).thenReturn(missingContacts)
        whenever(contactAsyncClient.findLocalContacts(any(), any())).thenReturn(FindLocalContactsResponse(apiContacts))

        runFindPlatformContacts()

        verify(contactsPersistenceManager).add(capture<Collection<ContactInfo>> {
            assertThat(it).apply {
                `as`("Contacts should be added")
                containsOnly(contactInfo)
            }
        })
    }
}