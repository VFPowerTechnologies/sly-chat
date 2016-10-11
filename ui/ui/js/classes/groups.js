var GroupDetails = function (conversation) {
    this.group = new Group(conversation.group);
    this.groupInfo = new GroupInfo(conversation.info);
    this.members = [];
    if (conversation.members !== undefined)
        this.setMembers(conversation.members);
};

GroupDetails.prototype = {
    setGroupInfo : function (info) {
        this.groupInfo.setInfo(info);
    },

    resetGroupInfo : function () {
        this.groupInfo.reset();
    },

    setMembers : function (members) {
        this.members = [];
        members.forEach(function (member) {
            this.addMember(new Contact(member));
        }.bind(this));
    },

    addMember : function (groupMember) {
        this.members[groupMember.id] = groupMember;
    },

    isActualConversation : function () {
        return this.groupInfo.lastTimestamp !== null;
    }
};

var Group = function (group) {
    this.id = group.id || null;
    this.name = group.name || null;
};

var GroupInfo = function (groupInfo) {
    this.setInfo(groupInfo);
};

GroupInfo.prototype = {
    setLastSpeaker : function (lastSpeaker) {
        this.lastSpeaker = lastSpeaker;
    },

    setUnreadMessageCount : function (unreadMessageCount) {
        this.unreadMessageCount = unreadMessageCount;
    },

    setLastMessage : function (lastMessage) {
        this.lastMessage = lastMessage;
    },

    setLastTimestamp : function (lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    },

    setInfo : function (info) {
        this.lastSpeaker = info.lastSpeaker;
        this.unreadMessageCount = info.unreadMessageCount;
        this.lastMessage = info.lastMessage;
        this.lastTimestamp = info.lastTimestamp;
    },

    reset : function () {
        this.setLastMessage(null);
        this.setLastTimestamp(null);
        this.setLastSpeaker(null);
        this.setUnreadMessageCount(0);
    }
};