var ChatModel = function () {
    this.cachedConversation = [];
};

ChatModel.prototype = {
    setController : function (controller) {
        this.controller = controller;
    },
    fetchMessage : function (start, count, contact) {
        if(this.cachedConversation[contact.id] === undefined) {
            messengerService.getLastMessagesFor(contact, start, count).then(function (messages) {
                var organizedMessages = this.organizeMessages(messages);
                this.storeCachedConversation(organizedMessages, contact);

                this.controller.displayMessage(organizedMessages, contact);
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.error("Unable to fetch messages: " + e);
            });
        }
        else {
            this.controller.displayMessage(this.cachedConversation[contact.id], contact);
        }
    },
    markConversationAsRead : function (contact) {
        messengerService.markConversationAsRead(contact).catch(function (e) {
            console.log(e);
            KEYTAP.exceptionController.displayDebugMessage(e);
        })
    },
    fetchConversationMessages : function (conversations) {
        var actualConversation = KEYTAP.recentChatController.model.orderByRecentChat(conversations);
        actualConversation.forEach(function (conversation) {
            if(typeof this.cachedConversation[conversation.contact.id] === "undefined") {
                messengerService.getLastMessagesFor(conversation.contact, this.controller.currentMessagePosition, this.controller.fetchingNumber).then(function (messages) {
                    this.storeCachedConversation(this.organizeMessages(messages), conversation.contact);
                }.bind(this)).catch(function (e) {
                    KEYTAP.exceptionController.displayDebugMessage(e);
                    console.error("Unable to fetch messages: " + e);
                });
            }
        }.bind(this));
    },
    storeCachedConversation : function (messages, contact) {
        if(Object.size(this.cachedConversation) <= 5) {
            this.cachedConversation[contact.id] = messages;
        }
    },
    pushNewMessage : function (contactId, message) {
        if(this.cachedConversation[contactId] !== undefined) {
            this.cachedConversation[contactId][message.id] = message;
        }
    },
    updateMessage : function (contactId, message) {
        if(this.cachedConversation[contactId] !== undefined) {
            this.cachedConversation[contactId][message.id] = message;
        }
    },
    organizeMessages : function (messages) {
        messages.reverse();

        var organizedMessages = [];
        messages.forEach(function (message) {
            organizedMessages[message.id] = message;
        });

        return organizedMessages;
    },
    getMessage : function (contact, messageId) {
        var message = this.cachedConversation[contact.id][messageId];

        if(typeof message !== "undefined" && message !== null)
            return message;
        else
            return null;
    },
    clearCache : function () {
        this.cachedConversation = [];
    }
};