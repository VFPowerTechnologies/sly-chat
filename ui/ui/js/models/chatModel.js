var ChatModel = function () {};

ChatModel.prototype = {
    setController : function (controller) {
        this.controller = controller;
    },
    fetchMessage : function (start, count, contact) {
        messengerService.getLastMessagesFor(contact, start, count).then(function (messages) {
            this.controller.displayMessage(messages, contact);//replaced by
        }.bind(this)).catch(function (e) {
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.error("Unable to fetch messages: " + e);
        });
    },
    markConversationAsRead : function (contact) {
        messengerService.markConversationAsRead(contact).catch(function (e) {
            console.log(e);
            KEYTAP.exceptionController.displayDebugMessage(e);
        })
    },
    fetchConversationMessages : function (conversation) {
        conversation.forEach(function (convo) {
            if(typeof this.fetchedConversation[convo.contact.email] === "undefined") {
                messengerService.getLastMessagesFor(convo.contact, this.controller.currentMessagePosition, this.controller.fetchingNumber).then(function (messages) {
                }.bind(this)).catch(function (e) {
                    KEYTAP.exceptionController.displayDebugMessage(e);
                    console.error("Unable to fetch messages: " + e);
                });
            }
        }.bind(this));
    }
};