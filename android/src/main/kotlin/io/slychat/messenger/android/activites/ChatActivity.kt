package io.slychat.messenger.android.activites

import android.content.*
import android.os.Bundle
import android.os.CountDownTimer
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.AlertDialog
import android.support.v7.widget.Toolbar
import android.view.*
import android.widget.*
import hani.momanii.supernova_emoji_library.Actions.EmojIconActions
import hani.momanii.supernova_emoji_library.Helper.EmojiconEditText
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.AndroidContactServiceImpl
import io.slychat.messenger.android.activites.services.impl.AndroidGroupServiceImpl
import io.slychat.messenger.android.activites.services.impl.AndroidMessengerServiceImpl
import io.slychat.messenger.android.activites.services.impl.AndroidConfigServiceImpl
import io.slychat.messenger.android.formatTimeStamp
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.contacts.ContactEvent
import io.slychat.messenger.services.messaging.ConversationMessage
import io.slychat.messenger.services.messaging.GroupEvent
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import android.view.animation.AnimationUtils
import io.slychat.messenger.services.config.ConvoTTLSettings
import android.view.ContextMenu.ContextMenuInfo
import io.slychat.messenger.core.condError
import io.slychat.messenger.core.isNotNetworkError

class ChatActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        val EXTRA_ISGROUP = "io.slychat.messenger.android.activities.ChatActivity.isGroup"
        val EXTRA_CONVERSTATION_ID = "io.slychat.messenger.android.activities.ChatActivity.converstationId"
    }
    private val log = LoggerFactory.getLogger(javaClass)

    private lateinit var app: AndroidApp
    private lateinit var messengerService: AndroidMessengerServiceImpl
    private lateinit var contactService: AndroidContactServiceImpl
    private lateinit var groupService: AndroidGroupServiceImpl
    private lateinit var configService: AndroidConfigServiceImpl

    private lateinit var contactInfo: ContactInfo
    private lateinit var groupInfo: GroupInfo

    private var groupMembers: Map<UserId, ContactInfo>? = null

    private var messagesCache = mutableMapOf<MessageId, ConversationMessageInfo>()

    lateinit var conversationId: ConversationId
    private var chatDataLink: MutableMap<String, Int> = mutableMapOf()
    private var contextMenuMessageId: String? = null

    private var expireToggled = false
    private var expireDelay: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log.debug("onCreate")

        val layoutId: Int
        val isGroup = intent.getBooleanExtra(EXTRA_ISGROUP, false)
        if (isGroup) {
            val gIdString = intent.getStringExtra(EXTRA_CONVERSTATION_ID)
            if (gIdString == null)
                finish()
            conversationId = GroupId(gIdString).toConversationId()
            layoutId = R.layout.activity_group_chat
        }
        else {
            val uIdLong = intent.getLongExtra(EXTRA_CONVERSTATION_ID, -1L)
            if (uIdLong == -1L)
                finish()
            conversationId = UserId(uIdLong).toConversationId()
            layoutId = R.layout.activity_user_chat
        }

        app = AndroidApp.get(this)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(layoutId)

        val actionBar = findViewById(R.id.chat_toolbar) as Toolbar
        actionBar.title = ""
        setSupportActionBar(actionBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setNavigationMenu()

        setupEmojicon()

        messengerService = AndroidMessengerServiceImpl(this)
        contactService = AndroidContactServiceImpl(this)
        groupService = AndroidGroupServiceImpl(this)
        configService = AndroidConfigServiceImpl(this)

        getDisplayInfo()
        createEventListeners()
    }

    private fun setupEmojicon() {
        val icons: String
        val tabs: String
        val bg: String
        val keyboard: Int
        val smiley: Int
        val currentTheme = app.appComponent.appConfigService.appearanceTheme

        if (currentTheme.isNullOrEmpty() || currentTheme == AndroidConfigServiceImpl.darkTheme) {
            icons = "#FFFFFF"
            tabs = "#222222"
            bg = "#222222"
            smiley = R.drawable.ic_tag_faces
            keyboard = R.drawable.ic_keyboard
        }
        else {
            icons = "#222222"
            tabs = "#FFFFFF"
            bg = "#FFFFFF"
            smiley = R.drawable.ic_tag_faces_black
            keyboard = R.drawable.ic_keyboard_black
        }

        val chatInput = findViewById(R.id.chat_input) as EditText
        val rootView = findViewById(R.id.chat_root_view)
        val emojiButton = findViewById(R.id.chat_emoji_button) as ImageButton
        val emojiInput = chatInput as EmojiconEditText

        val emojIcon = EmojIconActions(this, rootView, emojiInput, emojiButton, icons, tabs, bg)
        emojIcon.ShowEmojIcon()
        emojIcon.setIconsIds(keyboard, smiley)
    }

    private fun setNavigationMenu() {
        val navigationView: NavigationView
        if (conversationId is ConversationId.User) {
            navigationView = findViewById(R.id.chat_user_nav_view) as NavigationView
        }
        else {
            navigationView = findViewById(R.id.chat_group_nav_view) as NavigationView
        }

        navigationView.setNavigationItemSelectedListener(this)

        val drawerName = navigationView.getHeaderView(0).findViewById(R.id.drawer_user_name) as TextView
        val drawerEmail = navigationView.getHeaderView(0).findViewById(R.id.drawer_user_email) as TextView

        drawerEmail.text = app.accountInfo?.email
        drawerName.text = app.accountInfo?.name
    }

    fun getDisplayInfo() {
        val cId = conversationId
        if (cId is ConversationId.User) {
            contactService.getContact(cId.id) successUi {
                if (it == null)
                    finish()
                else {
                    contactInfo = it
                    val actionBar = findViewById(R.id.chat_toolbar) as Toolbar
                    actionBar.title = it.name
                }
            } failUi {
                log.error("Something failed: {}", it.message, it)
                finish()
            }
        }
        else if (cId is ConversationId.Group){
            groupService.getGroupInfo(cId.id) successUi {
                if (it != null) {
                    groupInfo = it
                    val actionBar = findViewById(R.id.chat_toolbar) as Toolbar
                    actionBar.title = it.name
                }
                else
                    finish()
            } failUi {
                log.error("Something failed: {}", it.message, it)
                finish()
            }
        }
    }

    private fun createEventListeners() {
        val submitBtn = findViewById(R.id.submit_chat_btn) as ImageButton
        val expireBtn = findViewById(R.id.expire_chat_btn) as ImageButton
        val expireSlider = findViewById(R.id.expiration_slider) as SeekBar

        submitBtn.setOnClickListener {
            handleNewMessageSubmit()
        }

        expireBtn.setOnClickListener {
            handleExpireMessageToggle()
        }

        expireSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                setExpireDelay(seekBar.progress.toLong())
                configService.setConvoTTLSettings(conversationId, ConvoTTLSettings(true, seekBar.progress.toLong() * 1000))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                setExpireDelay(seekBar.progress.toLong())
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                setExpireDelay(progress.toLong())
            }
        })
    }

    private fun init() {
        val cId = conversationId
        setAppActivity()
        setListeners()
        messengerService.fetchMessageFor(conversationId, 0, 100) successUi { messages ->
            cacheMessages(messages)
            if (cId is ConversationId.Group) {
                groupService.getMembersInfo(cId.id) successUi { members ->
                    groupMembers = members
                    displayMessages(messages)
                }
            }
            else
                displayMessages(messages)
        }

        displayExpireSliderOnStart()
    }

    private fun cacheMessages(messages: List<ConversationMessageInfo>) {
        messages.forEach { message ->
            addMessageToCache(message)
        }
    }

    private fun addMessageToCache(message: ConversationMessageInfo) {
        messagesCache.put(MessageId(message.info.id), message)
    }

    private fun displayExpireSliderOnStart() {
        val ttlConfig = configService.getConvoTTLSettings(conversationId)
        if (ttlConfig != null && ttlConfig.isEnabled) {
            setExpireDelay(ttlConfig.lastTTL/1000)
            showExpirationSlider()
        }
    }

    private fun handleExpireMessageToggle() {
        val ttlConfig = configService.getConvoTTLSettings(this.conversationId)
        if (ttlConfig != null)
            expireDelay = ttlConfig.lastTTL
        else
            expireDelay = 30000L

        if (expireToggled)
            hideExpirationSlider()
        else
            showExpirationSlider()
    }

    private fun showExpirationSlider() {
        val expireSlider = findViewById(R.id.expiration_slider) as SeekBar
        val expirationSliderContainer = findViewById(R.id.expiration_slider_container)

        var delay = expireDelay
        if (delay == null)
            delay = 5000

        expireSlider.progress = delay.toInt() / 1000
        setExpireDelay(delay / 1000)

        configService.setConvoTTLSettings(conversationId, ConvoTTLSettings(true, delay))

        expirationSliderContainer.visibility = View.VISIBLE
        expireToggled = true
    }

    private fun setExpireDelay(delay: Long) {
        expireDelay = delay * 1000

        val expirationDelay = findViewById(R.id.expiration_delay) as TextView
        expirationDelay.text = delay.toString()
    }

    private fun hideExpirationSlider() {
        val settingsTTl = configService.getConvoTTLSettings(conversationId)
        if (settingsTTl != null)
            configService.setConvoTTLSettings(conversationId, ConvoTTLSettings(false, settingsTTl.lastTTL))

        val expirationSliderContainer = findViewById(R.id.expiration_slider_container)
        expirationSliderContainer.visibility = View.GONE
        expireToggled = false
    }

    private fun displayMessages(messages: List<ConversationMessageInfo>) {
        val chatList = findViewById(R.id.chat_list) as LinearLayout
        chatList.removeAllViews()
        messages.reversed().forEach { message ->
            chatList.addView(createMessageNode(message))
            scrollToBottom()
        }
    }

    private fun createMessageNode(messageInfo: ConversationMessageInfo): View {
        val layout : Int
        if (messageInfo.info.isSent)
            layout = R.layout.sent_message_node
        else
            layout = R.layout.received_message_node

        val chatList = findViewById(R.id.chat_list) as LinearLayout
        val messageNode = LayoutInflater.from(this).inflate(layout, chatList, false)
        val messageIdNode = messageNode.findViewById(R.id.message_id) as TextView
        val message = messageNode.findViewById(R.id.message) as TextView
        val timespan = messageNode.findViewById(R.id.timespan) as TextView

        val nodeId = View.generateViewId()
        chatDataLink.put(messageInfo.info.id, nodeId)
        messageNode.id = nodeId

        messageIdNode.text = messageInfo.info.id

        val messageLayout = messageNode.findViewById(R.id.message_node_layout) as LinearLayout

        registerForContextMenu(messageNode)

        if (messageInfo.info.isExpired) {
            message.text = resources.getString(R.string.chat_expired_message_text)
            timespan.visibility = View.GONE
            messageLayout.visibility = View.VISIBLE
            return messageNode
        }

        val speaker = messageInfo.speaker
        val members = groupMembers
        if (speaker !== null && conversationId is ConversationId.Group && members !== null) {
            val contact = members[speaker]
            if (contact !== null) {
                val speakerName = messageNode.findViewById(R.id.chat_group_speaker_name) as TextView
                speakerName.visibility = View.VISIBLE
                speakerName.text = contact.name
            }
        }

        val time: String

        if (messageInfo.info.receivedTimestamp == 0L)
            time = resources.getString(R.string.chat_delivering_time_string)
        else
            time = formatTimeStamp(messageInfo.info.receivedTimestamp)

        timespan.text = time
        message.text = messageInfo.info.message

        if (messageInfo.info.ttlMs > 0 && !messageInfo.info.isSent && messageInfo.info.expiresAt <= 0) {
            val expirationLayout = messageNode.findViewById(R.id.expiring_message_layout) as LinearLayout
            expirationLayout.visibility = View.VISIBLE
            messageNode.setOnClickListener {
                showExpiringMessage(it, messageInfo)
            }

            val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
            messageNode.startAnimation(pulse)
        }
        else {
            if (messageInfo.info.expiresAt > 0) {
                val delayLayout = messageNode.findViewById(R.id.chat_message_expire_time_layout)
                val mTimer = delayLayout.findViewById(R.id.expire_seconds_left) as TextView
                val timeLeft = messageInfo.info.expiresAt - System.currentTimeMillis()
                startMessageExpirationCountdown(timeLeft, mTimer)

                delayLayout.visibility = View.VISIBLE
            }
            messageLayout.visibility = View.VISIBLE
        }

        return messageNode
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val messageIdNode = v.findViewById(R.id.message_id) as TextView
        contextMenuMessageId = messageIdNode.text.toString()

        val inflater = menuInflater
        inflater.inflate(R.menu.message_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val messageId = contextMenuMessageId
        when (item.itemId) {
            R.id.delete_single_message -> {
                if (messageId != null) {
                    deleteMessage(messageId)
                }
                return true
            }
            R.id.view_message_info -> {
                if (messageId != null) {
                    val messageInfo = messagesCache[MessageId(messageId)]
                    if (messageInfo != null)
                        startMessageInfoActivity(messageInfo)
                }
                return true
            }
            R.id.copy_message_text -> {
                if (messageId != null) {
                    val messageInfo = messagesCache[MessageId(messageId)]
                    if (messageInfo != null) {
                        copyMessageText(messageInfo.info.message)
                    }
                }
                return true
            }
            else -> return super.onContextItemSelected(item)
        }
    }

    private fun deleteMessage(messageId: String) {
        val dialog = android.app.AlertDialog.Builder(this)
                .setTitle(resources.getString(R.string.delete_message_title))
                .setMessage(resources.getString(R.string.delete_message_text))
                .setPositiveButton(resources.getString(R.string.ok_button), object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        messengerService.deleteMessage(conversationId, messageId) successUi {
                            val chatList = findViewById(R.id.chat_list) as LinearLayout
                            val nodeId = chatDataLink[messageId]
                            if (nodeId != null) {
                                val messageView = chatList.findViewById(nodeId)
                                chatList.removeView(messageView)
                                chatDataLink.remove(messageId)
                            }
                        } failUi {
                            log.error("Something failed: {}", it.message, it)
                        }
                    }
                })
                .create()

        dialog.show()
    }

    private fun startMessageInfoActivity(messageInfo: ConversationMessageInfo) {
        val intent = Intent(baseContext, MessageInfoActivity::class.java)
        intent.putExtra(MessageInfoActivity.EXTRA_SENT_TIME, messageInfo.info.timestamp)
        intent.putExtra(MessageInfoActivity.EXTRA_MESSAGE_ID, messageInfo.info.id)
        intent.putExtra(MessageInfoActivity.EXTRA_IS_SENT, messageInfo.info.isSent)
        intent.putExtra(MessageInfoActivity.EXTRA_RECEIVED_TIME, messageInfo.info.receivedTimestamp)
        intent.putExtra(MessageInfoActivity.EXTRA_SPEAKER_ID, messageInfo.speaker?.long)

        val cId = conversationId
        intent.putExtra(MessageInfoActivity.EXTRA_CONVERSTATION_ID, cId.asString())

        startActivity(intent)
    }

    private fun copyMessageText(message: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clipData = ClipData.newPlainText("", message)

        clipboardManager.primaryClip = clipData
    }

    private fun showExpiringMessage(node: View, messageInfo: ConversationMessageInfo) {
        messengerService.startMessageExpiration(conversationId, messageInfo.info.id) successUi {
            val messageLayout = node.findViewById(R.id.message_node_layout)
            val expireLayout = node.findViewById(R.id.expiring_message_layout)

            expireLayout.visibility = View.GONE
            messageLayout.visibility = View.VISIBLE
            node.clearAnimation()
        } failUi {
            log.error("Something failed: {}", it.message, it)
        }
    }

    private fun handleNewMessageSubmit() {
        var ttl = 0L
        val chatInput = findViewById(R.id.chat_input) as EditText
        val messageValue = chatInput.text.toString()
        if (messageValue.isEmpty())
            return

        val delay = expireDelay
        if (expireToggled && delay != null) {
            ttl = delay
            configService.setConvoTTLSettings(this.conversationId, ConvoTTLSettings(true, ttl))
        }

        messengerService.sendMessageTo(conversationId, messageValue, ttl) successUi {
            chatInput.setText("")
        } failUi {
            log.condError(isNotNetworkError(it), "${it.message}", it)
        }
    }

    private fun scrollToBottom() {
        val chatScrollView = findViewById(R.id.chat_list_scrollview) as ScrollView
        chatScrollView.post {
            chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun onNewMessage(newMessageInfo: ConversationMessage) {
        val cId = newMessageInfo.conversationId
        if (cId == conversationId) {
            addMessageToCache(newMessageInfo.conversationMessageInfo)
            handleNewMessageDisplay(newMessageInfo)
        }
    }

    private fun onContactEvent(event: ContactEvent) {
        if (event is ContactEvent.Blocked || event is ContactEvent.Removed) {
            finish()
        }
    }

    private fun onGroupEvent(event: GroupEvent) {
        if (event is GroupEvent.Blocked || event is GroupEvent.Parted) {
            finish()
        }
    }

    private fun onMessageUpdate(event: MessageUpdateEvent) {
        when (event) {
            is MessageUpdateEvent.Delivered -> { handleDeliveredMessageEvent(event) }
            is MessageUpdateEvent.Expired -> { handleExpiredMessage(event) }
            is MessageUpdateEvent.Deleted -> { handleDeletedMessage(event) }
            is MessageUpdateEvent.DeletedAll -> { handleDeletedAllMessage(event) }
            is MessageUpdateEvent.DeliveryFailed -> { handleFailedDelivery(event) }
            is MessageUpdateEvent.Expiring -> { handleMessageExpiringEvent(event) }
        }
    }

    private fun handleMessageExpiringEvent(event: MessageUpdateEvent.Expiring) {
        val nodeId = chatDataLink[event.messageId]
        if (nodeId != null) {
            val mMessageNode = findViewById(nodeId)
            val mDelayLayout = mMessageNode.findViewById(R.id.chat_message_expire_time_layout)
            val mTimer = mDelayLayout.findViewById(R.id.expire_seconds_left) as TextView

            startMessageExpirationCountdown(event.ttl, mTimer)

            mDelayLayout.visibility = View.VISIBLE
        }
    }

    private fun startMessageExpirationCountdown(delay: Long, textView: TextView) {

        object : CountDownTimer(delay, 1000) {
            override fun onTick(millisLeft: Long) {
                val secondsLeft = (millisLeft / 1000).toInt()
                textView.text = secondsLeft.toString()
            }

            override fun onFinish() {
                //
            }
        }.start()
    }

    private fun handleDeliveredMessageEvent(event: MessageUpdateEvent.Delivered) {
        val cId = event.conversationId
        if (cId == conversationId) {
            updateMessageDelivered(event)
        }
    }

    private fun handleFailedDelivery(event: MessageUpdateEvent.DeliveryFailed) {
        val nodeId = chatDataLink[event.messageId]
        if (nodeId === null)
            return

        val node = findViewById(nodeId)
        val timespanNode = node.findViewById(R.id.timespan) as TextView
        timespanNode.text = resources.getString(R.string.chat_failed_message_delivery)
    }

    private fun handleDeletedAllMessage(event: MessageUpdateEvent.DeletedAll) {
        val cId = event.conversationId
        if (cId == conversationId) {
            val chatList = findViewById(R.id.chat_list) as LinearLayout
            chatList.removeAllViews()
            messagesCache.clear()
        }
    }

    private fun handleDeletedMessage(event: MessageUpdateEvent.Deleted) {
        val cId = event.conversationId
        if (cId == conversationId) {
            event.messageIds.forEach {
                val messageId = MessageId(it)
                messagesCache.remove(messageId)

                val nodeId = chatDataLink[it]
                if (nodeId != null) {
                    val chatList = findViewById(R.id.chat_list) as LinearLayout
                    chatList.removeView(findViewById(nodeId))
                }
            }
        }
    }

    private fun handleExpiredMessage(event: MessageUpdateEvent.Expired) {
        val nodeId = chatDataLink[event.messageId]
        if (nodeId === null)
            return

        val messageNode = findViewById(nodeId) as LinearLayout
        val delayNode = messageNode.findViewById(R.id.chat_message_expire_time_layout)
        val message = messageNode.findViewById(R.id.message) as TextView
        val timespan = messageNode.findViewById(R.id.timespan) as TextView
        if (conversationId is ConversationId.Group) {
            val speakerName = messageNode.findViewById(R.id.chat_group_speaker_name) as TextView
            speakerName.visibility = View.GONE
        }
        message.text = resources.getString(R.string.chat_expired_message_text)
        timespan.text = ""
        timespan.visibility = View.GONE

        delayNode.visibility = View.GONE
    }

    private fun updateMessageDelivered(event: MessageUpdateEvent.Delivered) {
        val nodeId = chatDataLink[event.messageId]
        if (nodeId === null)
            return

        val node = findViewById(nodeId)
        (node.findViewById(R.id.timespan) as TextView).text = formatTimeStamp(event.deliveredTimestamp)
    }

    private fun handleNewMessageDisplay(newMessage: ConversationMessage) {
        val chatList = findViewById(R.id.chat_list) as LinearLayout
        chatList.addView(createMessageNode(newMessage.conversationMessageInfo))

        scrollToBottom()
    }

    private fun setListeners() {
        messengerService.addNewMessageListener({ onNewMessage(it) })
        messengerService.addMessageUpdateListener({ onMessageUpdate(it) })
        contactService.addContactListener { onContactEvent(it) }
        groupService.addGroupListener { onGroupEvent(it) }
    }

    private fun clearListners() {
        messengerService.clearListeners()
        contactService.clearListeners()
        groupService.clearListeners()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.layout_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_menu -> {
                val drawer = findViewById(R.id.chat_drawer_layout) as DrawerLayout
                drawer.openDrawer(Gravity.END)
            }
            android.R.id.home -> { finish() }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadContactInfo() {
        val cId = conversationId
        if (cId is ConversationId.User) {
            val intent = Intent(baseContext, ContactInfoActivity::class.java)
            intent.putExtra(ContactInfoActivity.EXTRA_USER_ID, cId.id.long)
            intent.putExtra(ContactInfoActivity.EXTRA_USER_NAME, contactInfo.name)
            intent.putExtra(ContactInfoActivity.EXTRA_USER_EMAIL, contactInfo.email)
            intent.putExtra(ContactInfoActivity.EXTRA_USER_PUBKEY, contactInfo.publicKey)
            startActivity(intent)
        }
    }

    private fun blockContact() {
        val cId = conversationId
        if (cId is ConversationId.User) {
            contactService.blockContact(cId.id) failUi {
                log.error("Something failed: {}", it.message, it)
            }
        }
    }

    private fun deleteConversation() {
        messengerService.deleteConversation(conversationId) failUi {
            log.error("Something failed: {}", it.message, it)
        }
    }

    private fun deleteContact() {
        contactService.deleteContact(contactInfo) successUi {
            if (!it)
                log.warn("Failed to delete user id : ${contactInfo.id}")
        } failUi {
            log.error("Something failed: {}", it.message, it)
        }
    }

    private fun deleteGroup() {
        val cId = conversationId
        if (cId is ConversationId.Group) {
            groupService.deleteGroup(cId.id) successUi {
                finish()
            } failUi {
                log.error("Something failed: {}", it.message, it)
            }
        }
    }

    private fun blockGroup() {
        val cId = conversationId
        if (cId is ConversationId.Group) {
            groupService.blockGroup(cId.id) failUi {
                log.error("Something failed: {}", it.message, it)
            }
        }
    }

    private fun loadGroupInfo() {
        val cId = conversationId
        if (cId is ConversationId.Group) {
            val intent = Intent(baseContext, GroupInfoActivity::class.java)
            intent.putExtra(GroupInfoActivity.EXTRA_GROUP_ID, cId.id.string)
            startActivity(intent)
        }
    }

    private fun openConfirmationDialog(title: String, message: String, callBack: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton(android.R.string.yes, DialogInterface.OnClickListener { dialog: DialogInterface, whichButton: Int ->
                callBack()
        }).setNegativeButton(android.R.string.no, null).show()
    }

    override fun onBackPressed() {
        val drawer = findViewById(R.id.chat_drawer_layout) as DrawerLayout
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_block_contact -> { openConfirmationDialog(resources.getString(R.string.chat_block_contact_title), resources.getString(R.string.chat_block_contact_text), { blockContact() }) }
            R.id.menu_delete_contact -> { openConfirmationDialog(resources.getString(R.string.chat_delete_contact_title), resources.getString(R.string.chat_delete_contact_text), { deleteContact() }) }
            R.id.menu_delete_conversation -> { openConfirmationDialog(resources.getString(R.string.chat_delete_conversation_title), resources.getString(R.string.chat_delete_conversation_text), { deleteConversation() }) }
            R.id.menu_contact_info -> { loadContactInfo() }
            R.id.menu_group_info -> { loadGroupInfo() }
            R.id.menu_delete_group -> { openConfirmationDialog(resources.getString(R.string.chat_delete_group_title), resources.getString(R.string.chat_delete_group_text), { deleteGroup() })}
            R.id.menu_block_group -> { openConfirmationDialog(resources.getString(R.string.chat_block_group_title), resources.getString(R.string.chat_block_group_text), { blockGroup() })}
        }

        val drawer = findViewById(R.id.chat_drawer_layout) as DrawerLayout
        drawer.closeDrawer(GravityCompat.END)
        return true
    }

    override fun onPause() {
        super.onPause()
        clearListners()
    }

    override fun onResume() {
        super.onResume()
        init()
    }
}