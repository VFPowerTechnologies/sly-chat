package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.persistence.json.JsonAccountInfoPersistenceManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonAccountInfoPersistenceManagerTest {
    val deviceId = 1
    val accountInfo = AccountInfo(UserId(1), "name", "email", "000-000-0000", deviceId)
    lateinit var accountManager: JsonAccountInfoPersistenceManager
    lateinit var tempFile: File

    @Before
    fun before() {
        tempFile = createTempFile()
        accountManager = JsonAccountInfoPersistenceManager(tempFile)
    }

    @After
    fun after() {
        tempFile.delete()
    }

    @Test
    fun `retrieve should return null if no account info was set and no file exists`() {
        val accountManager = JsonAccountInfoPersistenceManager(File("/tdfafafdasfsdff"))
        assertNull(accountManager.retrieve().get())
    }

    @Test
    fun `retrieve should return null when reading from an empty file`() {
        assertNull(accountManager.retrieve().get())
    }

    @Test
    fun `retrieve should return stored account info`() {
        accountManager.store(accountInfo).get()
        assertEquals(accountInfo, accountManager.retrieve().get())
    }

    @Test
    fun `store should update cached version`() {
        accountManager.store(accountInfo).get()
        val modified = accountInfo.copy(name = "test")
        accountManager.store(modified).get()
        assertEquals(modified, accountManager.retrieve().get())
    }
}