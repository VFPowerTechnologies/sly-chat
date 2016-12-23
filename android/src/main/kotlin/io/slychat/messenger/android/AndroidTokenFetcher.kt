package io.slychat.messenger.android

import android.content.Context
import com.google.android.gms.gcm.GoogleCloudMessaging
import com.google.android.gms.iid.InstanceID
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task

class AndroidTokenFetcher(
    private val context: Context
) : TokenFetcher {
    override fun canRetry(exception: Exception): Boolean {
        return true
    }

    override fun fetch(): Promise<String, Exception> = task {
        val instanceId = InstanceID.getInstance(context)
        val defaultSenderId = context.getString(R.string.gcm_defaultSenderId)

        instanceId.getToken(
            defaultSenderId,
            GoogleCloudMessaging.INSTANCE_ID_SCOPE,
            null
        )
    }

    override fun isInterestingException(exception: Exception): Boolean {
        return when (exception.message) {
            InstanceID.ERROR_BACKOFF -> false
            InstanceID.ERROR_SERVICE_NOT_AVAILABLE -> false
            InstanceID.ERROR_TIMEOUT -> false
            //shouldn't occur, but even if it did we have no info to go on anyways
            null -> false
            else -> true
        }
    }
}