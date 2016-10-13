var Conversation = function (conversation) {
    this.contact = new Contact(conversation.contact);
    this.info = new ConversationInfo(conversation.status);
};

Conversation.prototype = {
    isActualConversation : function () {
        return this.info.lastTimestamp !== null;
    },

    setInfo : function (info) {
        this.info = new ConversationInfo(info);
    },

    resetInfo : function () {
        this.info.reset();
    }
};

var Contact = function (contact) {
    this.id = contact.id || null;
    this.name = contact.name || null;
    this.email = contact.email || null;
    this.phoneNumber = contact.phoneNumber;
    this.publicKey = contact.publicKey;
    this.allowedMessageLevel = contact.allowedMessageLevel;
};

Contact.prototype = {
    block : function () {
        this.allowedMessageLevel = "BLOCKED";
    }
};

var ConversationInfo = function (info) {
    this.lastMessage = info.lastMessage || null;
    this.lastTimestamp = info.lastTimestamp || null;
    this.online = info.online || null;
    this.unreadMessageCount = info.unreadMessageCount || 0;
};

ConversationInfo.prototype = {
    setLastMessage : function (lastMessage) {
        this.lastMessage = lastMessage;
    },

    setLastTimestamp : function (lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    },

    setUnreadMessageCount : function (unreadMessageCount) {
        this.unreadMessageCount = unreadMessageCount;
    },

    reset : function () {
        this.setLastMessage(null);
        this.setLastTimestamp(null);
        this.setUnreadMessageCount(0);
    }
};