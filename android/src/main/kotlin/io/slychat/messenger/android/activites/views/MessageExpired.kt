package io.slychat.messenger.android.activites.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import io.slychat.messenger.android.R

class MessageExpired(
        sent: Boolean?,
        context: Context,
        attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    constructor(context: Context, attrs: AttributeSet?): this(null, context, attrs) {}

    init {
        if (sent == true)
            LayoutInflater.from(context).inflate(R.layout.message_sent_expired, this, true)
        else if (sent == false)
            LayoutInflater.from(context).inflate(R.layout.message_received_expired, this, true)
    }

}