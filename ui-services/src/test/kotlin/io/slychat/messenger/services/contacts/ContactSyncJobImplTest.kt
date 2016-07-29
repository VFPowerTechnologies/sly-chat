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

class ContactSyncJobImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        val keyVault = generateNewKeyVault("test")
    }

    val contactAsyncClient: ContactAsyncClient = mock()
    val addressBookAsyncClient: AddressBookAsyncClient = mock()
    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val userLoginData = UserData(SlyAddress(randomUserId(), 1), keyVault)
    val accountInfoPersistenceManager: AccountInfoPersistenceManager = mock()
    val platformContacts: PlatformContacts = mock()

    @Before
    fun before() {
        whenever(accountInfoPersistenceManager.retrieve()).thenReturn(
            AccountInfo(userLoginData.userId, "name", "email", "15555555555", 1)
        )

        whenever(platformContacts.fetchContacts()).thenReturn(emptyList())

        whenever(contactsPersistenceManager.findMissing(any())).thenReturn(listOf())
        whenever(contactsPersistenceManager.add(any<Collection<ContactInfo>>())).thenReturn(emptySet())
        whenever(contactsPersistenceManager.getRemoteUpdates()).thenReturn(emptyList())
        whenever(contactsPersistenceManager.applyDiff(any(), any())).thenReturn(Unit)
        whenever(contactsPersistenceManager.exists(anySet())).thenAnswerSuccess {
            val a = it.arguments[0]
            @Suppress("UNCHECKED_CAST")
            (a as Set<UserId>)
        }

        whenever(contactAsyncClient.findLocalContacts(any(), any())).thenReturn(FindLocalContactsResponse(emptyList()))
        whenever(contactAsyncClient.fetchContactInfoById(any(), any())).thenReturn(FetchContactInfoByIdResponse(emptyList()))

        whenever(addressBookAsyncClient.getContacts(any())).thenReturn(GetAddressBookResponse(emptyList()))
    }

    fun newJob(): ContactSyncJobImpl {
        return ContactSyncJobImpl(
            MockAuthTokenManager(),
            contactAsyncClient,
            addressBookAsyncClient,
            contactsPersistenceManager,
            userLoginData,
            accountInfoPersistenceManager,
            platformContacts
        )
    }

    fun runJobWithDescription(body: ContactSyncJobDescription.() -> Unit) {
        val syncJob = newJob()

        val description = ContactSyncJobDescription()
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
        val remoteEntries = encryptRemoteContactEntries(keyVault, missing.map { RemoteContactUpdate(it, AllowedMessageLevel.ALL) })

        whenever(addressBookAsyncClient.getContacts(any())).thenReturn(GetAddressBookResponse(remoteEntries))
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
        val remoteEntries = encryptRemoteContactEntries(keyVault, missing.map { RemoteContactUpdate(it, messageLevels[it]!!) })

        whenever(addressBookAsyncClient.getContacts(any())).thenReturn(GetAddressBookResponse(remoteEntries))
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
        val remoteUpdates = present.zip(messageLevels).map { RemoteContactUpdate(it.first, it.second) }
        val remoteEntries = encryptRemoteContactEntries(keyVault, remoteUpdates)

        whenever(addressBookAsyncClient.getContacts(any())).thenReturn(GetAddressBookResponse(remoteEntries))
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
    fun `an update remote sync should not issue a remote request if no missing platform contacts are found`() {
        runUpdateRemote()

        verify(addressBookAsyncClient, never()).updateContacts(any(), any())
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