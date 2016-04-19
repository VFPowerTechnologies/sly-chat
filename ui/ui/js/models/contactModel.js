var ContactModel = function () {
    this.conversations = [];
    this.currentContact = null;
};

ContactModel.prototype = {
    fetchConversation : function () {
        messengerService.getConversations().then(function(conversations){
            KEYTAP.recentChatController.init(conversations);
            KEYTAP.chatController.model.fetchConversationMessages(conversations);
            var forContact = this.orderByName(conversations);

            forContact.forEach(function(conversation){
                this.conversations[conversation.contact.id] = conversation;
            }.bind(this));

            this.controller.displayContacts(forContact);
        }.bind(this)).catch(function(e){
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log("Unable to fetch conversations: " + e);
        });
    },
    getConversations : function () {
        return this.conversations;
    },
    resetContacts : function () {
        this.conversations = [];
    },
    setController : function (controller) {
        this.controller = controller;
    },
    setCurrentContact : function (id) {
        this.currentContact = this.conversations[id].contact;
    },
    getCurrentContact : function () {
        return this.currentContact;
    },
    getContact : function (id) {
        var conversation = this.conversations[id];
        if (!conversation)
            return null;
        else
            return conversation.contact;
    },
    fetchConversationForChat : function (id, pushCurrentPage) {
        messengerService.getConversations().then(function(conversations){
            conversations.forEach(function(conversation){
                KEYTAP.contactController.model.conversations[conversation.contact.id] = conversation;
            });
            KEYTAP.contactController.model.setCurrentContact(id);
            KEYTAP.navigationController.loadPage("chat.html", pushCurrentPage);
        }.bind(id)).catch(function(e){
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log("Unable to fetch conversations: " + e);
        });
    },
    orderByName : function (conversations) {
        conversations.sort(function(a, b) {
            var emailA = a.contact.email.toLowerCase();
            var emailB = b.contact.email.toLowerCase();

            if(emailA < emailB)
                return -1;
            if(emailA > emailB)
                return 1;

            return 0;
        });

        return conversations;
    },
    clearCache : function () {
        this.resetContacts();
        this.currentContact = null;
    }
};
