package io.slychat.messenger.services.ui

enum class UIContactEventType {
    ADD,
    REQUEST
}

interface UIContactEvent {
    val type: UIContactEventType

    class Added(val contacts: List<UIContactDetails>) : UIContactEvent {
        override val type = UIContactEventType.ADD
    }

    class Request(val contacts: List<UIContactDetails>) : UIContactEvent {
        override val type = UIContactEventType.REQUEST
    }

}
