package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.persistence.ContactDiffDelta
import io.slychat.messenger.core.persistence.GroupDiffDelta

/**
 * @property fullPull True if our local address book version was out of date with the remote version.
 */
data class PullResults(
    val fullPull: Boolean = false,
    val contactDeltas: List<ContactDiffDelta> = emptyList(),
    val groupDeltas: List<GroupDiffDelta> = emptyList()
)