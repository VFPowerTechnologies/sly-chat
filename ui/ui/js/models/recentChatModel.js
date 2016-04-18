var RecentChatModel = function () {};

RecentChatModel.prototype = {
    setRecentChat : function (conversations) {
        var conversation = this.orderByRecentChat(conversations);
        var recentChat = [];

        conversation.forEach(function (chat) {
            recentChat[chat.contact.id] = chat;
        });

        KEYTAP.chatController.model.fetchConversationMessages(conversation);

        this.controller.displayRecentChat(recentChat);
    },
    setController : function (controller) {
        this.controller = controller;
    },
    orderByRecentChat : function (conversations) {
        var actualConversation = [];

        conversations.forEach(function (conversation) {
            if(conversation.status.lastMessage != null)
                actualConversation.push(conversation);
        });

        actualConversation.sort(function(a, b) {
            var dateA = parseFormatedTimeString(a.status.lastTimestamp);
            var dateB = parseFormatedTimeString(b.status.lastTimestamp);

            if(dateA.getTime() > dateB.getTime())
                return -1;
            if(dateA.getTime() < dateB.getTime())
                return 1;

            return 0;
        });

        return actualConversation;
    }
};
