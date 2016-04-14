var ChatController = function (model, contactController) {
    this.model = model;
    this.model.setController(this);
    this.contactController = contactController;
    this.currentMessagePosition = 0;
    this.fetchingNumber = 100;
    this.lastMessage = null;
};

ChatController.prototype = {
    init : function () {
        var contact = this.contactController.getCurrentContact();
        this.model.fetchMessage(this.currentMessagePosition, this.fetchingNumber, contact);
        this.model.markConversationAsRead(this.contactController.getCurrentContact());

        $("#newMessageSubmitBtn").click(function (){
            $("#newMessageInput").focus();
        });

        $("#newMessageForm").submit(function () {
            $("#newMessageInput").trigger("click");
            this.submitNewMessage();
            return false;
        }.bind(this));

        $('#newMessageInput').on("keypress", function(e) {
            if  (e.keyCode === 13 && !e.ctrlKey) {
                e.preventDefault();
                $("#newMessageForm").submit();
            }
        });
    },
    displayMessage : function (messages, contact) {
        var messageNode = $("#messages");

        var fragment = $(document.createDocumentFragment());
        this.lastMessage = null;
        for (var i = messages.length - 1; i >= 0; --i) {
            fragment.append(this.createMessageNode(messages[i], contact.name));
        }
        messageNode.html(fragment);
        this.scrollTop();
    },
    createChatLinkEvent : function () {
        // Removed from init to prevent multiple event binding
        // Chat message binding to open new browser page with url
        $(document).on("click", ".chatLink", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadMessageLink(this.href);
        });
    },
    createMessageNode : function (message, contactName) {
        if(message.sent == true)
            contactName = KEYTAP.userInfoController.getUserInfo().name;
        
        var fromClass = "";

        var node = $(document.createElement("li"));
        node.attr("id", "message_" + message.id);

        if(message.timestamp != null)
            message.time = message.timestamp;
        else
            message.time = null;

        if(this.lastMessage != null) {
            if(this.lastMessage.sent == true && message.sent == true) {
                if(message.time == null || this.lastMessage.time == null)
                    fromClass = "message-right following-message";
                else if(this.compareTime(this.lastMessage.time, message.time) == true)
                    fromClass = "message-right following-message";
                else {
                    fromClass = "message-right first-message";
                    node.append(createAvatar(contactName));
                }
            }
            else if(this.lastMessage.sent == true && message.sent == false) {
                fromClass = "message-left first-message";
                node.append(createAvatar(contactName));
            }
            else if(this.lastMessage.sent == false && message.sent == true) {
                fromClass = "message-right first-message";
                node.append(createAvatar(contactName));
            }
            else if(this.lastMessage.sent == false && message.sent == false) {
                if(this.compareTime(this.lastMessage.time, message.time) == true)
                    fromClass = "message-left following-message";
                else {
                    fromClass = "message-left first-message";
                    node.append(createAvatar(contactName));
                }
            }
        }
        else {
            if(message.sent == true) {
                fromClass = "message-right first-message";
                node.append(createAvatar(contactName));
            }
            else {
                fromClass = "message-left first-message";
                node.append(createAvatar(contactName));
            }
        }

        node.addClass(fromClass);

        var messageDiv = $(document.createElement("div"));
        messageDiv.addClass("message ");

        messageDiv.append("<p>" + formatTextForHTML(message.message) + "</p>");

        var timespan = $(document.createElement("span"));
        timespan.addClass("timespan");

        if(message.timestamp != null){
            timespan.html($.timeago(new Date(message.timestamp).toISOString()));
        }else if(message.timestamp == null){
            if(message.sent == true)
                timespan.html("Delivering...");
            else
                timespan.html(message.timestamp);
        }

        messageDiv.append(timespan);

        node.append(messageDiv);

        this.lastMessage = message;

        return $("<div/>").append(node).html();
    },
    submitNewMessage : function () {
        var message = $('#newMessageInput').val();
        if(message != ""){
            var currentContact = this.contactController.getCurrentContact();

            messengerService.sendMessageTo(currentContact, message).then(function (messageDetails) {
                this.model.pushNewMessage(currentContact.email, messageDetails);
                var input = $('#newMessageInput');
                input.val("");
                $("#messages").append(this.createMessageNode(messageDetails, KEYTAP.userInfoController.getUserInfo().name));
                this.scrollTop();
                input.click();
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.log(e);
            });
        }
    },
    compareTime : function (t1, t2) {
        if(t1 > t2)
            return false;
        else if(t2 - t1 > 300000)
            return false;
        else if(t2 - t1 < 300000)
            return true;

        return false;
    },
    addMessageUpdateListener : function () {
        messengerService.addMessageStatusUpdateListener(function (messageInfo) {
            messageInfo.messages.forEach(function (message) {
                this.model.updateMessage(messageInfo.contact, message);
                var messageDiv = $("#message_" + message.id);

                if(messageDiv.length && message.sent == true){
                    messageDiv.find(".timespan").html($.timeago(new Date(message.timestamp).toISOString()));
                }
            }.bind(this));
        }.bind(this));
    },
    addNewMessageListener : function () {
        messengerService.addNewMessageListener(this.handleNewMessageDisplay.bind(this));
    },
    handleNewMessageDisplay : function(messageInfo) {
        if(messageInfo.messages.length <= 0)
            return;

        var messages = messageInfo.messages;
        var contact = messageInfo.contact;

        //Update the cached messageList
        messages.forEach(function (message) {
            this.model.pushNewMessage(contact, message);
        }.bind(this));

        //Get the contact that sent the message
        var cachedContact = this.contactController.getContact(contact);
        if(!cachedContact)
            return;
        var contactName = cachedContact.name;

        this.updateChatPageNewMessage(messages, contactName, contact);
        this.updateContactPageNewMessage(contact);
        this.updateRecentChatNewMessage(messages, contact);
    },
    updateRecentChatNewMessage : function (messages, contactEmail) {
        var recentContent = $("#recentChatList");
        if(recentContent.length){
            var recentBlock = $("[id='recent%" + contactEmail + "']");
            if(recentBlock.length) {
                var contactDiv = recentBlock.find(".contact");
                if (!recentBlock.hasClass("new-messages")) {
                    recentBlock.addClass("new-messages");
                    contactDiv.after("<span class='pull-right label label-warning' style='line-height: 0.8'>" + "new" + "</span>");
                }

                contactDiv.find(".recentTimestamp").html($.timeago(new Date(messages[messages.length - 1].timestamp).toISOString()));
                contactDiv.find(".recentMessage").html(messages[messages.length - 1].message);
            }
        }
    },
    updateContactPageNewMessage : function (contactEmail) {
        var contactContent = $("#contactList");
        if(contactContent.length){
            var contactBlock = $("[id='contact%" + contactEmail + "']");
            if(contactBlock.length) {
                if (!contactBlock.hasClass("new-messages")) {
                    var contactDiv = contactBlock.find(".contact");
                    contactBlock.addClass("new-messages");
                    contactDiv.after("<span class='pull-right label label-warning' style='line-height: 0.8'>" + "new" + "</span>");
                }
            }
        }
    },
    updateChatPageNewMessage : function (messages, contactName, contactEmail) {
        var currentPageEmail = $("#currentPageChatEmail");
        if(currentPageEmail.length && currentPageEmail.html() == contactEmail){
            var messageDiv = $("#messages");

            if(messageDiv.length){
                //for the common case
                if(messages.length == 1) {
                    var message = messages[0];
                    messageDiv.append(this.createMessageNode(message, contactName));
                }
                else {
                    var fragment = $(document.createDocumentFragment());
                    messages.forEach(function (message) {
                        fragment.append(this.createMessageNode(message, contactName));
                    }, this);
                    messageDiv.append(fragment);
                }
                this.scrollTop();
                this.model.markConversationAsRead(this.contactController.getCurrentContact());
            }
        }
    },
    scrollTop : function () {
        var lastMessage = $("ul#messages li:last");

        if(typeof lastMessage != "undefined" && typeof lastMessage.offset() != "undefined")
            $("#content").scrollTop(lastMessage.offset().top + $(".chat").height());
    },
    clearCache : function () {
        this.model.clearCache();
    }
};
