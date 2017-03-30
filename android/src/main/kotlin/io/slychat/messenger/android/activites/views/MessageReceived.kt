package io.slychat.messenger.android.activites.views

import android.content.Context
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.ChatActivity
import io.slychat.messenger.android.activites.services.AndroidUIMessageInfo
import io.slychat.messenger.android.formatTimeStamp
import io.slychat.messenger.core.persistence.ConversationId

class MessageReceived(
        messageInfo: AndroidUIMessageInfo?,
        context: Context,
        attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    constructor(context: Context, attrs: AttributeSet?): this(null, context, attrs) {}

    private val mMessageId: TextView
    private val mMessageLayout: LinearLayout
    private val mMessage: TextView
    private val mTimespan: TextView
    private val mSpeakerName: TextView
    private var mExpirationLayout: LinearLayout? = null
    private val mDelayLayout: View
    private val mTimer: TextView
    private val chatActivity: ChatActivity

    init {
        LayoutInflater.from(context).inflate(R.layout.message_received, this, true)

        mMessageId = this.findViewById(R.id.message_id) as TextView
        mMessageLayout = this.findViewById(R.id.message_node_layout) as LinearLayout
        mMessage = this.findViewById(R.id.message) as TextView
        mTimespan = this.findViewById(R.id.timespan) as TextView
        mSpeakerName = this.findViewById(R.id.chat_group_speaker_name) as TextView
        mDelayLayout = this.findViewById(R.id.chat_message_expire_time_layout)
        mTimer = this.findViewById(R.id.expire_seconds_left) as TextView
        mExpirationLayout = this.findViewById(R.id.expiring_message_layout) as LinearLayout

        chatActivity = context as ChatActivity

        if (messageInfo != null)
            setMessageLayout(messageInfo)
    }

    private fun setMessageLayout(message: AndroidUIMessageInfo) {
        mMessageId.text = message.messageId

        setGroupSpeakerName(message)
        setTimestamp(message)

        if (message.ttl > 0 && !message.isSent && message.expiresAt <= 0) {
            mExpirationLayout?.visibility = View.VISIBLE
            this.setOnClickListener {
                chatActivity.displayExpiringMessage(message.messageId)
            }

            val pulse = AnimationUtils.loadAnimation(context, R.anim.pulse)
            this.startAnimation(pulse)
        }
        else {
            if (message.expiresAt > 0) {
                val timeLeft = message.expiresAt - System.currentTimeMillis()
                startMessageExpirationCountdown(timeLeft)

                mDelayLayout.visibility = View.VISIBLE
            }

            mMessageLayout.visibility = View.VISIBLE
        }
    }

    private fun setGroupSpeakerName(message: AndroidUIMessageInfo) {
        val conversationId = chatActivity.conversationId
        if (conversationId is ConversationId.Group) {
            val speaker = message.speakerId
            val groupMembers = chatActivity.groupMembers
            if (speaker !== null) {
                val contact = groupMembers[speaker]
                if (contact !== null) {
                    mSpeakerName.text = contact.name
                    mSpeakerName.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setTimestamp(message: AndroidUIMessageInfo) {
        val time: String
        if (message.receivedTimestamp == 0L)
            time = resources.getString(R.string.chat_delivering_time_string)
        else
            time = formatTimeStamp(message.receivedTimestamp)

        mTimespan.text = time
        mMessage.text = message.message
    }

    fun startMessageExpirationCountdown(delay: Long) {
        object : CountDownTimer(delay, 1000) {
            override fun onTick(millisLeft: Long) {
                val secondsLeft = (millisLeft / 1000).toInt()
                mTimer.text = secondsLeft.toString()
            }

            override fun onFinish() {
                //
            }
        }.start()

        mDelayLayout.visibility = View.VISIBLE
    }
}