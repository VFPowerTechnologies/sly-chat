var ChatModel = function () {
    this.fetchedConversation = [];
    this.arrayMap = []; // Map for fetchedConversation [message.id => fetchedConversation index]
};

ChatModel.prototype = {
    setController : function (controller) {
        this.controller = controller;
    },
    fetchMessage : function (start, count, contact) {
        if(typeof this.fetchedConversation[contact.email] != "undefined") {
            this.controller.displayMessage(this.fetchedConversation[contact.email], contact);
        }
        else {
            messengerService.getLastMessagesFor(contact, start, count).then(function (messages) {
                this.setFetchedConversation(contact.email, messages);
                this.controller.displayMessage(this.fetchedConversation[contact.email], contact);
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.error("Unable to fetch messages: " + e);
            });
        }
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
                    this.setFetchedConversation(convo.contact.email, messages);
                }.bind(this)).catch(function (e) {
                    KEYTAP.exceptionController.displayDebugMessage(e);
                    console.error("Unable to fetch messages: " + e);
                });
            }
        }.bind(this));
    },
    setFetchedConversation : function (email, messages) {
        this.fetchedConversation[email] = messages;
        this.mapFetchedConversation(email);
    },
    pushNewMessage : function (email, message) {
        if(typeof this.fetchedConversation[email] !== "undefined") {
            this.arrayMap[email][message.id] = this.fetchedConversation[email].unshift(message) -1;
            this.mapFetchedConversation(email);
        }
    },
    updateMessage : function (email, message) {
        if(typeof this.fetchedConversation[email] !== "undefined") {
            this.fetchedConversation[email][this.arrayMap[email][message.id]] = message;
        }
    },
    mapFetchedConversation : function (email) {
        var arrayMap = {};

        var i = 0;
        this.fetchedConversation[email].forEach(function (message) {
            arrayMap[message.id] = i;
            i++;
        });

        this.arrayMap[email] = arrayMap;
    }
};