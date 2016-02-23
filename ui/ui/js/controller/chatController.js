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
        this.newMessageInput = document.getElementById('newMessageInput');
        var self = this;
        $("#newMessageForm").submit(function () {
            self.submitNewMessage();
            return false;
        });
    },
    displayMessage : function (messages, contact) {
        var messagesNode = document.getElementById('messages');
        var messagesHtml = "";
        for (var i = messages.length - 1; i >= 0; --i) {
            messagesHtml += this.createMessageNode(messages[i], contact.name);
        }
        if(messagesHtml != ""){
            messagesNode.innerHTML = messagesHtml;
        }
        window.scrollTo(0,document.body.scrollHeight);
    },
    createMessageNode : function (message, contactName) {
        if(message.sent == true){
            fromClass = "message-left";
        }
        else{
            fromClass = "message-right";
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
            var messagesNode = document.getElementById('messages');
            messengerService.sendMessageTo(this.contactController.getCurrentContact(), message).then(function (messageDetails) {
                this.newMessageInput.value = "";
                messagesNode.innerHTML += this.createMessageNode(messageDetails, "me");
                window.scrollTo(0,document.body.scrollHeight);
                $("#newMessageInput").click();
            }.bind(this)).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.log(e);
            });
        }
    },
    addMessageUpdateListener : function () {
        messengerService.addMessageStatusUpdateListener(function (messageInfo) {
            messageDiv = document.getElementById("message_" + messageInfo.message.id);

            if(messageDiv != null && messageInfo.message.sent == true){
                messageDiv.getElementsByClassName("timespan")[0].innerHTML = messageInfo.message.timestamp;// + '<i class="mdi mdi-checkbox-marked-circle pull-right"></i>';
            }
        });
    },
    addNewMessageListener : function () {
        messengerService.addNewMessageListener(function (messageInfo) {
            if(document.getElementById("page-title") != null && document.getElementById("page-title").textContent == messageInfo.contact.name){
                var messagesDiv = document.getElementById("messages");
                if(messagesDiv != null){
                    messagesDiv.innerHTML += this.createMessageNode(messageInfo.message, messageInfo.contact.name);
                    window.scrollTo(0,document.body.scrollHeight);
                }
            }
            else if(document.getElementById("contact" + messageInfo.contact.email) != null){
                var contactBlock = document.getElementById("contact" + messageInfo.contact.email);
                contactBlock.className = contactBlock.className.replace("new-messages", "");
                contactBlock.className += " new-messages";

                var newBadge = "<span class='pull-right label label-warning'>" + "new" + "</span>";
                if(contactBlock.innerHTML.indexOf(newBadge) <= -1){
                    contactBlock.innerHTML += newBadge;
                }
            }
        }.bind(this));
    }
};