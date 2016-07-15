package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.services.GroupEvent
import nl.komponents.kovenant.Promise

@JSToJavaGenerate("GroupService")
interface UIGroupService {
    fun addGroupEventListener(listener: (GroupEvent) -> Unit)

    fun createNewGroup(name: String, initialMembers: List<UIContactDetails>)

    fun getGroups(): Promise<List<UIGroupInfo>, Exception>

    fun inviteUser(groupId: GroupId, contact: UIContactDetails)

    fun leaveGroup(groupId: GroupId)
}