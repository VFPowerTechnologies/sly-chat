package io.slychat.messenger.android.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.slychat.messenger.services.ui.UIPlatformService
import org.slf4j.LoggerFactory

class AndroidUIPlatformService(private val context: Context) : UIPlatformService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun openURL(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        if (intent.resolveActivity(context.packageManager) == null) {
            log.warn("Unable to find a browser")
            return
        }
        context.startActivity(intent)
    }
}