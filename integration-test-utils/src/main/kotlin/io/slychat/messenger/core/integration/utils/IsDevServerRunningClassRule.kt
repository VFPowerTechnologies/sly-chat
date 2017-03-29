package io.slychat.messenger.core.integration.utils

import org.junit.rules.ExternalResource

class IsDevServerRunningClassRule : ExternalResource() {
    override fun before() {
        isDevServerRunning()
    }
}

