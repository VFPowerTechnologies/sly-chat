package io.slychat.messenger.services.contacts

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.generateNewKeyVault
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

class AddressBookSyncJobImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        val keyVault = generateNewKeyVault("test")
    }

    val contactAsyncClient: ContactAsyncClient = mock()
    val addressBookAsyncClient: AddressBookAsyncClient = mock()
    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val groupPersistenceManager:  GroupPersistenceManager = mock()
    val userLoginData = UserData(SlyAddress(randomUserId(), 1), keyVault)
    val accountRegionCode = "1"
    val platformContacts: PlatformContacts = mock()

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

        whenever(addressBookAsyncClient.update(any(), any())).thenReturn(Unit)
        whenever(addressBookAsyncClient.get(any())).thenReturn(GetAddressBookResponse(emptyList()))
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
            platformContacts
        )
    }

    fun runJobWithDescription(body: AddressBookSyncJobDescription.() -> Unit) {
        val syncJob = newJob()

        val description = AddressBookSyncJobDescription()
        description.body()

        syncJob.run(description).get()

    }

    fun runUpdateRemote() {
        runJobWithDescription { doUpdateRemoteContactList() }
    }

    fun runPlatformContactSync() {
        runJobWithDescription { doPlatformContactSync() }
    }

    fun runRemoteSync() {
        runJobWithDescription { doRemoteSync() }
    }

    @Test
    fun `a remote sync should fetch any missing contact info`() {
        val missing = randomUserIds()
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, missing.map { AddressBookUpdate.Contact(it, AllowedMessageLevel.ALL) })

        whenever(addressBookAsyncClient.get(any())).thenReturn(GetAddressBookResponse(remoteEntries))
        whenever(contactsPersistenceManager.exists(missing)).thenReturn(emptySet())

        runRemoteSync()

        verify(contactAsyncClient).fetchContactInfoById(any(), capture {
            assertThat(it.ids).apply {
                `as`("Missing ids should be looked up")
                containsOnlyElementsOf(missing)
            }
        })
    }

    @Test
    fun `a remote sync should add missing contacts with the proper message levels`() {
        val missing = randomUserIds(3)
        val messageLevels = missing.zip(listOf(
            AllowedMessageLevel.ALL,
            AllowedMessageLevel.BLOCKED,
            AllowedMessageLevel.GROUP_ONLY
        )).toMap()

        val apiContacts = missing.map { ApiContactInfo(it, "$it@a.com", it.toString(), it.toString(), it.toString()) }
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, missing.map { AddressBookUpdate.Contact(it, messageLevels[it]!!) })

        whenever(addressBookAsyncClient.get(any())).thenReturn(GetAddressBookResponse(remoteEntries))
        whenever(contactsPersistenceManager.exists(missing)).thenReturn(emptySet())
        whenever(contactAsyncClient.fetchContactInfoById(any(), any())).thenReturn(FetchContactInfoByIdResponse(apiContacts))

        runRemoteSync()

        verify(contactsPersistenceManager).applyDiff(capture {
            assertEquals(missing.size, it.size, "New contacts size doesn't match")

            it.forEach {
                assertEquals(messageLevels[it.id]!!, it.allowedMessageLevel, "Invalid message level")
            }
        }, any())
    }

    @Test
    fun `a remote sync should update existing contacts with the proper message level`() {
        val present = randomUserIds(3)
        val messageLevels = listOf(AllowedMessageLevel.ALL, AllowedMessageLevel.GROUP_ONLY, AllowedMessageLevel.BLOCKED)
        val remoteUpdates = present.zip(messageLevels).map { AddressBookUpdate.Contact(it.first, it.second) }
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, remoteUpdates)

        whenever(addressBookAsyncClient.get(any())).thenReturn(GetAddressBookResponse(remoteEntries))
        whenever(contactsPersistenceManager.exists(present)).thenReturn(present)

        runRemoteSync()

        verify(contactsPersistenceManager).applyDiff(any(), capture {
            assertThat(it).apply {
                `as`("Existing contacts should have their message levels updated")
                containsOnlyElementsOf(remoteUpdates)
            }
        })
    }

    @Test
    fun `a remote sync not should add contacts if none are present`() {
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, emptyList())

        whenever(addressBookAsyncClient.get(any())).thenReturn(GetAddressBookResponse(remoteEntries))

        runRemoteSync()

        verify(contactsPersistenceManager, never()).applyDiff(any(), any())
    }

    @Test
    fun `a remote sync should add contacts before groups`() {
        val groupInfo = randomGroupInfo()

        val userId = randomUserId()
        val apiContacts = listOf(ApiContactInfo(userId, "$userId@a.com", userId.toString(), userId.toString(), userId.toString()))
        val remoteUpdates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), GroupMembershipLevel.JOINED),
            AddressBookUpdate.Contact(userId, AllowedMessageLevel.ALL)
        )

        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, remoteUpdates)

        whenever(addressBookAsyncClient.get(any())).thenReturn(GetAddressBookResponse(remoteEntries))
        whenever(contactAsyncClient.fetchContactInfoById(any(), any())).thenReturn(FetchContactInfoByIdResponse(apiContacts))

        runRemoteSync()

        val order = inOrder(contactsPersistenceManager, groupPersistenceManager)
        order.verify(contactsPersistenceManager).applyDiff(any(), any())
        order.verify(groupPersistenceManager).applyDiff(any())
    }

    @Test
    fun `a remote sync should add missing groups`() {
        val groupInfo = randomGroupInfo()

        val remoteUpdates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), GroupMembershipLevel.JOINED)
        )
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, remoteUpdates)

        whenever(addressBookAsyncClient.get(any())).thenReturn(GetAddressBookResponse(remoteEntries))

        runRemoteSync()

        verify(groupPersistenceManager).applyDiff(remoteUpdates)
    }

    @Test
    fun `a remote sync not should add groups if none are present`() {
        val remoteEntries = encryptRemoteAddressBookEntries(keyVault, emptyList())

        whenever(addressBookAsyncClient.get(any())).thenReturn(GetAddressBookResponse(remoteEntries))

        runRemoteSync()

        verify(groupPersistenceManager, never()).applyDiff(any())
    }

    @Test
    fun `an update remote sync should not issue a remote request if no missing platform contacts are found`() {
        runUpdateRemote()

        verify(addressBookAsyncClient, never()).update(any(), any())
    }

    @Test
    fun `an update remote sync should send group updates`() {
        val groupInfo = randomGroupInfo()

        val updates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), groupInfo.membershipLevel)
        )

        whenever(groupPersistenceManager.getRemoteUpdates()).thenReturn(updates)

        runUpdateRemote()

        val request = updateRequestFromAddressBookUpdates(keyVault, updates)
        verify(addressBookAsyncClient).update(any(), eq(request))
    }

    @Test
    fun `an update remote sync should send contact updates`() {
        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(updates)

        runUpdateRemote()

        val request = updateRequestFromAddressBookUpdates(keyVault, updates)
        verify(addressBookAsyncClient).update(any(), eq(request))
    }

    @Test
    fun `an update remote sync should delete group updates after a successful update`() {
        val groupInfo = randomGroupInfo()

        val updates = listOf(
            AddressBookUpdate.Group(groupInfo.id, groupInfo.name, emptySet(), groupInfo.membershipLevel)
        )

        whenever(groupPersistenceManager.getRemoteUpdates()).thenReturn(updates)

        runUpdateRemote()

        verify(groupPersistenceManager).removeRemoteUpdates(updates.map { it.groupId })
    }

    @Test
    fun `an update remote sync should delete contact updates after a successful update`() {
        val updates = listOf(
            AddressBookUpdate.Contact(randomUserId(), AllowedMessageLevel.ALL)
        )

        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(updates)

        runUpdateRemote()

        verify(contactsPersistenceManager).removeRemoteUpdates(updates.map { it.userId })
    }

    @Test
    fun `a remote sync should not issue a remote request if no contacts need to be added`() {
        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(emptyList())

        runRemoteSync()

        verify(contactAsyncClient, never()).fetchContactInfoById(any(), any())
    }

    @Test
    fun `a platform contact sync should not issue a remote request if no platform contacts are found`() {
        whenever(platformContacts.fetchContacts()).thenReturn(emptyList())

        runPlatformContactSync()

        verify(contactAsyncClient, never()).findLocalContacts(any(), any())
    }

    @Test
    fun `a platform contact sync should not issue a remote request if no missing local contacts are found`() {
        val platformContact = PlatformContact("name", listOf("a@a.com"), listOf("15555555555"))
        whenever(platformContacts.fetchContacts()).thenReturn(listOf(platformContact))
        whenever(contactsPersistenceManager.findMissing(anyList())).thenReturn(emptyList())

        runPlatformContactSync()

        verify(contactAsyncClient, never()).findLocalContacts(any(), any())
    }

    @Test
    fun `a platform contact sync should query for new contacts using missing local platform contact data`() {
        val platformContact = PlatformContact("name", listOf("a@a.com"), listOf("15555555555"))
        val missingContacts = listOf(platformContact)

        whenever(platformContacts.fetchContacts()).thenReturn(missingContacts)
        whenever(contactsPersistenceManager.findMissing(anyList())).thenReturn(missingContacts)

        runPlatformContactSync()

        verify(contactAsyncClient).findLocalContacts(any(), eq(FindLocalContactsRequest(missingContacts)))
    }

    @Test
    fun `a platform contact sync should add local contacts with remote accounts to the contact list with ALL message level`() {
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

        runPlatformContactSync()

        verify(contactsPersistenceManager).add(capture<Collection<ContactInfo>> {
            assertThat(it).apply {
                `as`("Contacts should be added")
                containsOnly(contactInfo)
            }
        })
    }
}