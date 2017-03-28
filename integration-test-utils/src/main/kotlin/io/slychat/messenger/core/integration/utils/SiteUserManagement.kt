package io.slychat.messenger.core.integration.utils

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.crypto.hashPasswordForRemoteWithDefaults
import io.slychat.messenger.core.http.api.registration.RegistrationInfo

class SiteUserManagement(private val devClient: DevClient) {
    private var currentUserId = 1L
    private var currentPhoneNumber = 1111111111

    val dummyRegistrationInfo = RegistrationInfo("c@a.com", "name", "000-000-0000")

    val defaultPassword = "test"

    fun nextUserId(): UserId {
        val r = currentUserId
        currentUserId += 1
        return UserId(r)
    }


    fun newSiteUser(registrationInfo: RegistrationInfo, password: String): GeneratedSiteUser {
        val keyVault = generateNewKeyVault(password)
        val serializedKeyVault = keyVault.serialize()
        val remotePasswordHashInfo = hashPasswordForRemoteWithDefaults(password)

        val user = SiteUser(
            nextUserId(),
            registrationInfo.email,
            remotePasswordHashInfo.params,
            keyVault.fingerprint,
            registrationInfo.name,
            registrationInfo.phoneNumber,
            serializedKeyVault
        )

        return GeneratedSiteUser(user, keyVault, remotePasswordHashInfo.hash)
    }

    fun injectSiteUser(registrationInfo: RegistrationInfo): GeneratedSiteUser {
        val siteUser = newSiteUser(registrationInfo, defaultPassword)

        devClient.addUser(siteUser)

        return siteUser
    }

    fun injectNamedSiteUser(email: String, phoneNumber: String? = null): GeneratedSiteUser {
        val number = if (phoneNumber == null) {
            currentPhoneNumber++
            currentPhoneNumber.toString()
        }
        else
            phoneNumber

        val registrationInfo = RegistrationInfo(email, "name", number)
        return injectSiteUser(registrationInfo)
    }

    fun injectNewSiteUser(): GeneratedSiteUser {
        return injectSiteUser(dummyRegistrationInfo)
    }
}