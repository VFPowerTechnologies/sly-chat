var ChatController = function (model, contactController) {
    this.model = model;
    this.contactController = contactController;
    this.currentMessagePosition = 0;
    this.fetchingNumber = 100;
};

ChatController.prototype = {
    init : function () {
        this.model.setController(this);
        this.model.fetchMessage(this.currentMessagePosition, this.fetchingNumber, this.contactController.getCurrentContact());
        this.model.markConversationAsRead(this.contactController.getCurrentContact());
        this.newMessageInput = document.getElementById('newMessageInput');
        var self = this;

        $("#newMessageSubmitBtn").click(function (){
            $("#newMessageInput").focus();
        });

        $("#newMessageForm").submit(function () {
            $("#newMessageInput").trigger("click");
            self.submitNewMessage();
            return false;
        });

        $("#newMessageInput").on("keypress", function(e) {
            if  (e.keyCode === 13 && !e.ctrlKey) {
                e.preventDefault();
                $("#newMessageForm").submit();
            }
        });
    },
    displayMessage : function (messages, contact) {
        var iframe = $("#chatContent");
        var messageNode = iframe.contents().find("#messages");

        var messagesHtml = "";
        for (var i = messages.length - 1; i >= 0; --i) {
            messagesHtml += this.createMessageNode(messages[i], contact.name);
        }
        if(messagesHtml != ""){
            messageNode.html(messagesHtml);
            document.getElementById("chatContent").contentWindow.scrollTo(0, 9999999);
        }
    },
    createMessageNode : function (message, contactName) {
        if(message.sent == true){
            fromClass = "message-right";
        }
        else{
            fromClass = "message-left";
        }
        var node = "<li id='message_" + message.id + "' class='" + fromClass + "'>";
        node += this.contactController.createAvatar(contactName);

        var msgDiv = document.createElement('div');
        msgDiv.setAttribute('class', 'message');

        var msgP = document.createElement('p');
        msgP.textContent = message.message;

        var timeSpan = document.createElement('span');

        if(message.timestamp != null){
            timeSpan.textContent = message.timestamp;
        }else{
            timeSpan.textContent = message.timestamp;
        }
        timeSpan.className = "timespan";

        msgDiv.innerHTML = msgP.outerHTML + timeSpan.outerHTML;

        node += msgDiv.outerHTML + "</li>";

        return node;
    },
    submitNewMessage : function () {
        var message = this.newMessageInput.value;
        if(message != ""){
            var messageNode = $("#chatContent").contents().find("#messages");

            messengerService.sendMessageTo(this.contactController.getCurrentContact(), message).then(function (messageDetails) {
                this.newMessageInput.value = "";
                messageNode.append(this.createMessageNode(messageDetails, "me"));
                document.getElementById("chatContent").contentWindow.scrollTo(0, 9999999);
                $("#newMessageInput").click();
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.log(e);
            });
        }
    },
    addMessageUpdateListener : function () {
        messengerService.addMessageStatusUpdateListener(function (messageInfo) {
            var messageDiv = $("#chatContent").contents().find("#message_" + messageInfo.message.id);

            if(messageDiv.length && messageInfo.message.sent == true){
                messageDiv.find(".timespan").html(messageInfo.message.timestamp);// + '<i class="mdi mdi-checkbox-marked-circle pull-right"></i>';
            }
        });
    },
    addNewMessageListener : function () {
        messengerService.addNewMessageListener(function (messageInfo) {
            if(document.getElementById("currentPageChatEmail") != null && document.getElementById("currentPageChatEmail").innerHTML == messageInfo.contact){
                var messageDiv = $("#chatContent").contents().find("#messages");
                if(messageDiv.length){
                    var contactName = this.contactController.getContact(messageInfo.contact).name;
                    messageDiv.append(this.createMessageNode(messageInfo.message, contactName));
                    document.getElementById("chatContent").contentWindow.scrollTo(0, 9999999);
                }
            }
            else if($("#contactContent").length){
                var contactBlock = $("#contactContent").contents().find("[id='contact%" + messageInfo.contact + "']");
                if(contactBlock.length) {
                    if (!contactBlock.hasClass("new-messages")) {
                        var contact = contactBlock.find(".contact");
                        contactBlock.addClass("new-messages");
                        contact.append("<span class='pull-right label label-warning' style='bottom: 5px;'>" + "new" + "</span>");
                    }
                }
            }
        }.bind(this));
    }
};