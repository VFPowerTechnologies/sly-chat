package io.slychat.messenger.services.ui

enum class UIContactEventType {
    ADD,
    REQUEST
}

interface UIContactEvent {
    val type: UIContactEventType
}

class UIContactsAdded(val contacts: List<UIContactDetails>) : UIContactEvent {
    override val type = UIContactEventType.ADD
}

class UIContactRequests(val contacts: List<UIContactDetails>) : UIContactEvent {
    override val type = UIContactEventType.REQUEST
}

