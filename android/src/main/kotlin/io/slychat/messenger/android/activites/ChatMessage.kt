package io.slychat.messenger.android.activites

import android.content.Context
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.LinearLayout
import android.widget.TextView
import io.slychat.messenger.android.R
import io.slychat.messenger.android.formatTimeStamp
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ConversationMessageInfo
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi

class ChatMessage(
        info: ConversationMessageInfo?,
        context: Context,
        attrs: AttributeSet? = null) : LinearLayout(context, attrs) {

    constructor(context: Context, attrs: AttributeSet?): this(null, context, attrs) {}

    companion object {
        val SENT_GRAVITY = Gravity.END
        val RECEIVED_GRAVITY = Gravity.START
    }

    private val chatActivity: ChatActivity
    lateinit var messageInfo: ConversationMessageInfo
    lateinit var conversationId: ConversationId
    var groupMembers: Map<UserId, ContactInfo>? = null

    val mMessageId: TextView
    val mMessageLayout: LinearLayout
    val mMessage: TextView
    val mTimespan: TextView
    val mSpeakerName: TextView
    var mExpirationLayout: LinearLayout? = null
    val mDelayLayout: View
    val mTimer: TextView

    init {
        if (info != null) {
            messageInfo = info
            if (info.info.isSent) {
                LayoutInflater.from(context).inflate(R.layout.sent_message_node, this, true)
                this.setGravity(SENT_GRAVITY)
            }
            else {
                LayoutInflater.from(context).inflate(R.layout.received_message_node, this, true)
                mExpirationLayout = this.findViewById(R.id.expiring_message_layout) as LinearLayout
                this.setGravity(RECEIVED_GRAVITY)
            }
        }

        mMessageId = this.findViewById(R.id.message_id) as TextView
        mMessageLayout = this.findViewById(R.id.message_node_layout) as LinearLayout
        mMessage = this.findViewById(R.id.message) as TextView
        mTimespan = this.findViewById(R.id.timespan) as TextView
        mSpeakerName = this.findViewById(R.id.chat_group_speaker_name) as TextView
        mDelayLayout = this.findViewById(R.id.chat_message_expire_time_layout)
        mTimer = this.findViewById(R.id.expire_seconds_left) as TextView

        chatActivity = context as ChatActivity
    }

    fun create(cId: ConversationId, members: Map<UserId, ContactInfo>?): LinearLayout {
        conversationId = cId
        groupMembers = members

        val nodeId = View.generateViewId()
        this.id = nodeId

        setMessageInfo()

        chatActivity.registerForContextMenu(this)

        return this
    }

    private fun setMessageInfo() {
        mMessageId.text = messageInfo.info.id

        if (messageInfo.info.isExpired) {
            setMessageExpired()
            return
        }

        setGroupSpeakerName()
        setTimestamp()

        if (messageInfo.info.ttlMs > 0 && !messageInfo.info.isSent && messageInfo.info.expiresAt <= 0) {
            mExpirationLayout?.visibility = View.VISIBLE
            this.setOnClickListener {
                displayExpiringMessage(messageInfo)
            }

            val pulse = AnimationUtils.loadAnimation(context, R.anim.pulse)
            this.startAnimation(pulse)
        }
        else {
            if (messageInfo.info.expiresAt > 0) {
                val timeLeft = messageInfo.info.expiresAt - System.currentTimeMillis()
                startMessageExpirationCountdown(timeLeft)

                mDelayLayout.visibility = View.VISIBLE
            }

            mMessageLayout.visibility = View.VISIBLE
        }

    }

    fun setMessageExpired() {
        mMessage.text = resources.getString(R.string.chat_expired_message_text)
        mTimespan.text = ""
        mTimespan.visibility = View.GONE
        mMessageLayout.visibility = View.VISIBLE
        mDelayLayout.visibility = View.GONE

        if (conversationId is ConversationId.Group) {
            val speakerName = this.findViewById(R.id.chat_group_speaker_name)
            speakerName.visibility = View.GONE
        }
    }

    fun setGroupSpeakerName() {
        val members = groupMembers
        val speaker = messageInfo.speaker
        if (speaker !== null && conversationId is ConversationId.Group && members !== null) {
            val contact = members[speaker]
            if (contact !== null) {
                mSpeakerName.visibility = View.VISIBLE
                mSpeakerName.text = contact.name
            }
        }
    }

    fun setTimestamp() {
        val time: String
        if (messageInfo.info.receivedTimestamp == 0L)
            time = resources.getString(R.string.chat_delivering_time_string)
        else
            time = formatTimeStamp(messageInfo.info.receivedTimestamp)

        mTimespan.text = time
        mMessage.text = messageInfo.info.message
    }

    fun updateTimeStamp(timeStamp: Long) {
        mTimespan.text = formatTimeStamp(timeStamp)
    }

    fun markFailedDelivery() {
        mTimespan.text = resources.getString(R.string.chat_failed_message_delivery)
    }

    fun displayExpiringMessage(messageInfo: ConversationMessageInfo) {
        chatActivity.messengerService.startMessageExpiration(conversationId, messageInfo.info.id) successUi {
            mExpirationLayout?.visibility = View.GONE
            mMessageLayout.visibility = View.VISIBLE
            this.clearAnimation()
        } failUi {
            chatActivity.log.error("Something failed: {}", it.message, it)
        }
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