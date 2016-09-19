var ChatController = function () {
    this.lastMessage = null;
};

ChatController.prototype = {
    init : function () {
        this.addMessageUpdateListener();
        this.addNewMessageListener();
    },

    addMessageUpdateListener : function () {
        messengerService.addMessageStatusUpdateListener(function (event) {
            switch (event.type) {
                case 'DELIVERED':
                    var messageBlock = $("#message_" + event.messageId);
                    if(messageBlock.length){
                        var time = new Date(event.deliveredTimestamp - window.relayTimeDifference).toISOString();
                        messageBlock.find(".timespan").html("<time class='timeago' datetime='" + time + "' title='" + $.timeago(time) + "'>" + $.timeago(time) + "</time>");
                    }

                    break;
            }

            contactController.resetCachedConversation();
        }.bind(this));
    },

    addNewMessageListener : function () {
        messengerService.addNewMessageListener(this.handleNewMessageDisplay.bind(this));
    },

    clearCache : function () {
        this.lastMessage = null;
    },

    createGroupMessageNode : function (message, group) {
        var classes = "";

        if(this.lastMessage == null)
            classes += " firstMessage";
        else {
            if ((message.info.sent && this.lastMessage.info.sent) || (!message.info.sent && !this.lastMessage.info.sent))
                classes += " followingMessage";
            else
                classes += " firstMessage";
        }


        if (message.info.sent === true)
            classes += " messageSent";
        else
            classes += " messageReceived";

        var timespan = "";
        if(message.info.sent && message.info.receivedTimestamp == 0){
            timespan = "Delivering...";
        }
        else {
            var time = new Date(message.info.timestamp - window.relayTimeDifference).toISOString();
            timespan = "<time class='timeago' datetime='" + time + "' title='" + $.timeago(time) + "'>" + $.timeago(time) + "</time>";
        }

        this.lastMessage = message;

        var contactName = "";
        if(message.speaker !== null) {
            var contact = contactController.getContact(message.speaker);
            if(contact !== false)
                contactName = "<p style='font-size: 10px; color: #9e9e9e;'>" + contact.name + "</p>";
        }

        var messageNode = $("<li id='message_" + message.info.id + "' class='" + classes + "'><div class='message'>" +
            contactName +
            "<p>" + formatTextForHTML(createTextNode(message.info.message)) + "</p>" +
            "<span class='timespan'>" + timespan + "</span>" +
            "</div></li>");

        messageNode.on("mouseheld", function () {
            vibrate(50);
            this.openGroupMessageMenu(message, group);
        }.bind(this));

        messageNode.find(".timeago").timeago();

        return messageNode;
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
            var time = new Date(message.timestamp - window.relayTimeDifference).toISOString();
            timespan = "<time class='timeago' datetime='" + time + "' title='" + $.timeago(time) + "'>" + $.timeago(time) + "</time>";
        }

        this.lastMessage = message;

        var messageNode = $("<li id='message_" + message.id + "' class='" + classes + "'><div class='message'>" +
            "<p>" + formatTextForHTML(createTextNode(message.message)) + "</p>" +
            "<span class='timespan'>" + timespan + "</span>" +
            "</div></li>");

        messageNode.on("mouseheld", function () {
            vibrate(50);
            this.openMessageMenu(message);
        }.bind(this));

        messageNode.find(".timeago").timeago();

        return messageNode;
    },

    deleteConversation : function (contact) {
        messengerService.deleteAllMessagesFor(contact.id).then(function () {
            contactController.resetCachedConversation();
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        }.bind(this));
    },

    deleteMessage : function (id, contact) {
        var messageIds = [];
        if(id.constructor !== Array)
            messageIds.push(id);
        else
            messageIds = id;

        messengerService.deleteMessagesFor(contact.id, messageIds).then(function () {
            messageIds.forEach(function (id) {
                $("#message_" + id).remove();
            });
        }.bind(this)).catch(function (e) {
            console.log("Could not delete messages for contact id " + contact.id);
            // TODO handle errors
        })
    },

    displayMessage : function (messages, contact, isGroup) {
        this.lastMessage = null;
        var frag = $(document.createDocumentFragment());

        if (isGroup === true) {
            for(var g in messages) {
                if (messages.hasOwnProperty(g)) {
                    frag.append(this.createGroupMessageNode(messages[g], contact));
                }
            }
        }
        else {
            for (var s in messages) {
                if (messages.hasOwnProperty(s)) {
                    frag.append(this.createMessageNode(messages[s], contact));
                }
            }
        }

        $("#chat-content").html(frag);
        this.scrollTop();
    },

    fetchMessageFor : function (start, count, contact) {
        messengerService.getLastMessagesFor(contact.id, start, count).then(function (messages) {
            var organizedMessages = this.organizeMessages(messages);
            this.displayMessage(organizedMessages, contact);

        }.bind(this)).catch(function (e) {
            console.error("Unable to fetch messages: " + e);
        });
    },

    getCurrentContactId : function () {
        if (navigationController.getCurrentPage() === "chat.html") {
            return $("#contact-id").html();
        }
        return false;
    },

    handleNewMessageDisplay : function(messageInfo) {
        if(messageInfo.messages.length <= 0)
            return;

        var messages = messageInfo.messages;
        var contactId = messageInfo.contact;

        //Get the contact that sent the message
        var cachedContact = contactController.getContact(contactId);

        if (messageInfo.groupId === null) {
            if(!cachedContact) {
                console.log("No cached contact for " + contactId);
                return;
            }

            this.updateChatPageNewMessage(messages, cachedContact.name, contactId);
        }
        else {
            this.updateGroupChatPageNewMessage(messageInfo);
            groupController.updateConversationWithNewMessage(messageInfo.groupId, messageInfo);
        }

        $(".timeago").timeago();
    },

    leftMenuAddNewMessageBadge : function (id) {
        var node = $("#leftContact_" + id);
        if (this.getCurrentContactId() != id) {
            if (node.find(".left-menu-new-badge").length <= 0)
                node.append('<span class="left-menu-new-badge" style="color: red; font-size: 12px; margin-left: 5px;">new</span>');
        }
    },

    leftMenuRemoveNewMessageBadge : function (id) {
        $("#leftContact_" + id).find(".left-menu-new-badge").remove();
    },

    openGroupMessageMenu : function (message, groupId) {
        var contact = contactController.getContact($('#contact-id').html());

        var buttons = [
            {
                text: 'Message Info',
                onClick: function () {
                    this.showGroupMessageInfo(message, groupId);
                }.bind(this)
            },
            {
                text: 'Delete message',
                onClick: function () {
                    slychat.confirm("Are you sure you want to delete this message?", function () {
                        groupController.deleteMessages(groupId, [message.info.id]);
                    }.bind(this))
                }.bind(this)
            },
            {
                text: 'Copy Text',
                onClick: function () {
                    copyToClipboard(message.info.message);
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

    organizeGroupMessages : function (messages) {
        messages.reverse();

        var organizedMessages = [];
        messages.forEach(function (message) {
            organizedMessages[message.info.id] = message;
        });

        return organizedMessages;
    },

    organizeMessages : function (messages) {
        messages.reverse();

        var organizedMessages = [];
        messages.forEach(function (message) {
            organizedMessages[message.id] = message;
        });

        return organizedMessages;
    },

    scrollTop : function () {
        var offset = $("#chat-content").height();
        $("[data-page='chat'] #chatPageContent").scrollTop(offset);
    },

    showGroupMessageInfo : function (message, groupId) {
        var contactDiv = "";
        var receivedTime = "";
        var groupName = "";
        var group = groupController.getGroup(groupId);
        var members = groupController.getGroupMembers(groupId);

        var memberList = "";

        members.forEach(function (member) {
            memberList += "<div class='member'>" +
                "<span>" + member.name + "</span>" +
                "<span>" + member.email + "</span>" +
                "</div>";
        });

        if (message.speaker === null){
            contactDiv = "<div class='message-info'>" +
                "<p class='message-info-title'>Sent To Group:</p>" +
                "<p class='message-info-details'>" + group.name + "</p>" +
                "</div>";
        }
        else {
            var contact = contactController.getContact(message.speaker);

            contactDiv = "<div class='message-info'>" +
                "<p class='message-info-title'>Contact Name:</p>" +
                "<p class='message-info-details'>" + contact.name + "</p>" +
                "</div>" +
                "<div class='message-info'>" +
                "<p class='message-info-title'>Contact Email:</p>" +
                "<p class='message-info-details'>" + contact.email + "</p>" +
                "</div>" +
                "<div class='message-info'>" +
                "<p class='message-info-title'>Contact Public Key:</p>" +
                "<p class='message-info-details'>" + formatPublicKey(contact.publicKey) + "</p>" +
                "</div>";

            receivedTime = "<div class='message-info'>" +
                "<p class='message-info-title'>Message Received Time:</p>" +
                "<p class='message-info-details'>" + message.info.receivedTimestamp + "</p>" +
                "</div>";

            groupName = "<div class='message-info'>" +
                "<p class='message-info-title'>Group Name:</p>" +
                "<p class='message-info-details'>" + group.name + "</p>" +
                "</div>";
        }

        var content = contactDiv +
            '<div class="message-info">' +
            '<p class="message-info-title">Message id:</p>'+
            '<p class="message-info-details">' + message.info.id + '</p>' +
            '</div>'+
            '<div class="message-info">' +
            '<p class="message-info-title">Sent Time:</p>'+
            '<p class="message-info-details">' + message.info.timestamp + '</p>' +
            '</div>' +
            receivedTime +
            '<div class="message-info">' +
            '<p class="message-info-title">Message is encrypted <i class="fa fa-check-square color-green"></i></p>' +
            '</div>' +
            "<div class='group-info'>" +
            "<p class='group-info-title'>Group Id:</p>" +
            "<p class='group-info-details'>" + group.id + "</p>" +
            "</div>" +
            groupName +
            '<div class="group-info">' +
            '<p class="group-info-title">Group Members:</p>'+
            '<div class="group-info-details"><div class="members">' + memberList + '</div></div>' +
            '</div>';

        openInfoPopup(content, "Message Info");
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

        openInfoPopup(content, "Message Info");
    },

    submitNewMessage : function (contact, message) {
        if (contact.email === undefined) {
            messengerService.sendGroupMessageTo(contact.id, message, 0).then(function () {
                var input = $("#newMessageInput");
                input.val("");
                input.click();
                this.scrollTop();
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
            })
        }
        else {
            messengerService.sendMessageTo(contact.id, message, 0).then(function () {
                var input = $("#newMessageInput");
                input.val("");
                input.click();
                this.scrollTop();
            }.bind(this)).catch(function (e) {
                console.log(e);
            });
        }
    },

    updateChatPageNewMessage : function (messages, contactName, contactId) {
        var currentPageContactId = $("#contact-id");
        if(navigationController.getCurrentPage() == "chat.html" && currentPageContactId.length && currentPageContactId.html() == contactId){
            var messageDiv = $("#chat-content");

            if(messageDiv.length){
                var contact = contactController.getContact(contactId);
                vibrate(100);

                var fragment = $(document.createDocumentFragment());
                messages.forEach(function (message) {
                    fragment.append(this.createMessageNode(message, contact));
                }, this);

                messageDiv.append(fragment);
                this.scrollTop();
            }
        }
    },

    updateGroupChatPageNewMessage : function (messagesInfo) {
        var messages = messagesInfo.messages;
        var currentPageContactId = $("#contact-id");
        if(navigationController.getCurrentPage() == "chat.html" && currentPageContactId.length && currentPageContactId.html() == messagesInfo.groupId){
            var messageDiv = $("#chat-content");

            if(messageDiv.length){
                var contact = contactController.getContact(messagesInfo.contact);
                vibrate(100);
                var fragment = $(document.createDocumentFragment());

                messages.forEach(function (message) {
                    var messageInfo = {
                        info: message,
                        speaker: messagesInfo.contact
                    };
                    fragment.append(this.createGroupMessageNode(messageInfo, contact));
                }, this);

                messageDiv.append(fragment);

                this.scrollTop();
            }
        }
    }
};
