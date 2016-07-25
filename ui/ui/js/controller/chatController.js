var ChatController = function () {
    this.chatCache = [];
    this.lastMessage = null;
};

ChatController.prototype = {
    init : function () {
        this.addMessageUpdateListener();
        this.addNewMessageListener();
    },

    fetchMessageFor : function (start, count, contact) {
        messengerService.getLastMessagesFor(contact, start, count).then(function (messages) {
            var organizedMessages = this.organizeMessages(messages);
            this.storeCachedConversation(organizedMessages, contact);
            this.displayMessage(organizedMessages, contact);

        }.bind(this)).catch(function (e) {
            console.error("Unable to fetch messages: " + e);
        });
    },

    submitNewMessage : function (contact, message) {
        messengerService.sendMessageTo(contact, message).then(function (messageDetails) {
            this.pushNewMessageInCache(contact.id, messageDetails);
            $("#chat-content").append(this.createMessageNode(messageDetails, profileController.name));

            var input = $("#newMessageInput");
            input.val("");
            input.click();
            this.scrollTop();
        }.bind(this)).catch(function (e) {
            console.log(e);
        });
    },

    displayMessage : function (messages, contact) {
        this.lastMessage = null;
        var frag = $(document.createDocumentFragment());
        for(var k in messages) {
            if(messages.hasOwnProperty(k)) {
                frag.append(this.createMessageNode(messages[k], contact));
            }
        }
        
        $("#chat-content").html(frag);
        this.scrollTop();
    },

    createMessageNode : function (message, contact) {
        var classes = "";

        if(this.lastMessage == null)
            classes += " firstMessage";
        else {
            if ((message.sent && this.lastMessage.sent) || (!message.sent && !this.lastMessage.sent))
                classes += " followingMessage";
            else
                classes += " firstMessage";
        }


        if (message.sent === true)
            classes += " messageSent";
        else
            classes += " messageReceived";

        var timespan = "";
        if(message.sent && message.receivedTimestamp == 0){
            timespan = "Delivering...";
        }
        else {
            var time = new Date(message.timestamp).toISOString();
            timespan = "<time class='timeago' datetime='" + time + "' title='" + $.timeago(time) + "'>" + $.timeago(time) + "</time>";
        }

        this.lastMessage = message;

        var messageNode = $("<li id='message_" + message.id + "' class='" + classes + "'><div class='message'>" +
            "<p>" + formatTextForHTML(createTextNode(message.message)) + "</p>" +
            "<span class='timespan'>" + timespan + "</span>" +
            "</div></li>");

        $$(messageNode).on("taphold", function () {
            vibrate(50);
            this.openMessageMenu(message);
        }.bind(this));

        return messageNode;
    },

    storeCachedConversation : function (messages, contact) {
        if(Object.size(this.chatCache) <= 5) {
            this.chatCache[contact.id] = messages;
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

    pushNewMessageInCache : function (contactId, message) {
        if(contactId in this.chatCache)
            this.chatCache[contactId][message.id] = message;
    },

    updateMessageCache : function (contactId, message) {
        if (contactId in this.chatCache && message.id in this.chatCache[contactId])
            this.chatCache[contactId][message.id] = message;
    },

    scrollTop : function () {
        var offset = $("#chat-content").height();
        $("[data-page='chat'] #chatPageContent").scrollTop(offset);
    },

    addMessageUpdateListener : function () {
        messengerService.addMessageStatusUpdateListener(function (messageInfo) {
            messageInfo.messages.forEach(function (message) {
                this.updateMessageCache(messageInfo.contact, message);
                var messageBlock = $("#message_" + message.id);
                if(messageBlock.length && message.sent == true){
                    var time = new Date(message.timestamp).toISOString();
                    messageBlock.find(".timespan").html("<time class='timeago' datetime='" + time + "' title='" + $.timeago(time) + "'>" + $.timeago(time) + "</time>");
                }
            }.bind(this));

            contactController.resetCachedConversation();
        }.bind(this));
    },

    addNewMessageListener : function () {
        messengerService.addNewMessageListener(this.handleNewMessageDisplay.bind(this));
    },

    handleNewMessageDisplay : function(messageInfo) {
        if(messageInfo.messages.length <= 0)
            return;

        var messages = messageInfo.messages;
        var contactId = messageInfo.contact;

        messages.forEach(function (message) {
            this.pushNewMessageInCache(contactId, message);
        }.bind(this));

        //Get the contact that sent the message
        var cachedContact = contactController.getContact(contactId);
        if(!cachedContact) {
            console.error("No cached contact for " + contactId);
            return;
        }
        var contactName = cachedContact.name;

        contactController.updateRecentChatNode(cachedContact, messageInfo);
        this.updateChatPageNewMessage(messages, contactName, contactId);

        $(".timeago").timeago();
    },

    updateChatPageNewMessage : function (messages, contactName, contactId) {
        var currentPageContactId = $("#contact-id");
        if(navigationController.getCurrentPage() == "chat.html" && currentPageContactId.length && currentPageContactId.html() == contactId){
            var messageDiv = $("#chat-content");

            if(messageDiv.length){
                var contact = contactController.getContact(contactId);
                vibrate(100);
                //for the common case
                if(messages.length == 1) {
                    var message = messages[0];
                    messageDiv.append(this.createMessageNode(message, contact));
                }
                else {
                    var fragment = $(document.createDocumentFragment());
                    messages.forEach(function (message) {
                        fragment.append(this.createMessageNode(message, contact));
                    }, this);
                    messageDiv.append(fragment);
                }
                this.scrollTop();
                this.markConversationAsRead(contact);
            }
        }
    },

    markConversationAsRead : function (contact) {
        messengerService.markConversationAsRead(contact).catch(function (e) {
            console.log(e);
        })
    },

    openMessageMenu : function (message) {
        var contact = contactController.getContact($('#contact-id').html());

        var buttons = [
            {
                text: 'Message Info',
                onClick: function () {
                    this.showMessageInfo(message, contact);
                }.bind(this)
            },
            {
                text: 'Delete message',
                onClick: function () {
                    slychat.confirm("Are you sure you want to delete this message?", function () {
                        // TODO update confirm style
                        this.deleteMessage(message.id, contact);
                    }.bind(this))
                }.bind(this)
            },
            {
                text: 'Copy Text',
                onClick: function () {
                    copyToClipboard(message.message);
                }.bind(this)
            },
            {
                text: 'Cancel',
                color: 'red',
                onClick: function () {
                }
            }
        ];
        slychat.actions(buttons);
    },

    deleteMessage : function (id, contact) {
        var messageIds = [];
        if(id.constructor !== Array)
            messageIds.push(id);
        else
            messageIds = id;

        messengerService.deleteMessagesFor(contact, messageIds).then(function () {
            messageIds.forEach(function (id) {
                $("#message_" + id).remove();
            });
        }.bind(this)).catch(function (e) {
            console.log("Could not delete messages for contact id " + contact.id);
            // TODO handle errors
        })
    },

    deleteConversation : function (contact) {
        messengerService.deleteAllMessagesFor(contact).then(function () {
            contactController.resetCachedConversation();
        }.bind(this)).catch(function (e) {
            // TODO handle errors
            console.log("couldn't delete the conversation ");
            console.log(e);
        }.bind(this));
    },

    showMessageInfo : function (message, contact) {
        var contactDiv = "";
        var receivedTime = "";

        if (message.sent === true){
            contactDiv = "<div class='message-info'>" +
                    "<p class='message-info-title'>Sent To:</p>" +
                    "<p class='message-info-details'>" + contact.name + "</p>" +
                "</div>";
        }
        else {
            contactDiv = "<div class='message-info'>" +
                    "<p class='message-info-title'>Contact Name:</p>" +
                    "<p class='message-info-details'>" + contact.name + "</p>" +
                "</div>" +
                "<div class='message-info'>" +
                    "<p class='message-info-title'>Contact Email:</p>" +
                    "<p class='message-info-details'>" + contact.email + "</p>" +
                "</div>";

            receivedTime = "<div class='message-info'>" +
                "<p class='message-info-title'>Message Received Time:</p>" +
                "<p class='message-info-details'>" + message.receivedTimestamp + "</p>" +
                "</div>";
        }

        var content = contactDiv +
            "<div class='message-info'>" +
                "<p class='message-info-title'>Contact Public Key:</p>" +
                "<p class='message-info-details'>" + formatPublicKey(contact.publicKey) + "</p>" +
            "</div>" +
            '<div class="message-info">' +
                '<p class="message-info-title">Message id:</p>'+
                '<p class="message-info-details">' + message.id + '</p>' +
            '</div>'+
            '<div class="message-info">' +
                '<p class="message-info-title">Sent Time:</p>'+
                '<p class="message-info-details">' + message.timestamp + '</p>' +
            '</div>' +
            receivedTime +
            '<div class="message-info">' +
                '<p class="message-info-title">Message is encrypted <i class="fa fa-check-square color-green"></i></p>' +
            '</div>';

        openInfoPopup(content);
    }
};