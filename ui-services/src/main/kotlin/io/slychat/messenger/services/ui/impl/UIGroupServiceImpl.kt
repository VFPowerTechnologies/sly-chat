package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.services.GroupEvent
import io.slychat.messenger.services.ui.UIContactDetails
import io.slychat.messenger.services.ui.UIGroupInfo
import io.slychat.messenger.services.ui.UIGroupService
import nl.komponents.kovenant.Promise

class UIGroupServiceImpl : UIGroupService {
    override fun addGroupEventListener(listener: (GroupEvent) -> Unit) {
        throw NotImplementedError()
    }

    override fun createNewGroup(name: String, initialMembers: List<UIContactDetails>) {
        throw NotImplementedError()
    }

    override fun getGroups(): Promise<List<UIGroupInfo>, Exception> {
        throw NotImplementedError()
    }

    override fun inviteUser(groupId: GroupId, contact: UIContactDetails) {
        throw NotImplementedError()
    }

    override fun leaveGroup(groupId: GroupId) {
        throw NotImplementedError()
    }
}