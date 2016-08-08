package io.slychat.messenger.services.ui.dummy

import io.slychat.messenger.services.ui.UIDevelService

class DummyNotAvailableException(val serviceName: String) : UnsupportedOperationException("Dummy for $serviceName is not loaded")

class UIDevelServiceImpl : UIDevelService {
}