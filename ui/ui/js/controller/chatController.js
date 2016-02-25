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
        $("html,body").animate({scrollTop: $("ul#messages li:last").offset().top});
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
            if(document.getElementById("currentPageChatEmail") != null && document.getElementById("currentPageChatEmail").innerHTML == messageInfo.contact.email){
                var messagesDiv = document.getElementById("messages");
                if(messagesDiv != null){
                    messagesDiv.innerHTML += this.createMessageNode(messageInfo.message, messageInfo.contact.name);
                    window.scrollTo(0,document.body.scrollHeight);
                }
            }
            else if(document.getElementById("contact%" + messageInfo.contact.email) != null){
                var contactBlock = $("div[id='contact%" + messageInfo.contact.email + "']");

                if(!contactBlock.hasClass("new-messages")){
                    var contact = contactBlock.find(".contact");
                    contactBlock.addClass("new-messages");
                    contact.append("<span class='pull-right label label-warning' style='bottom: 5px;'>" + "new" + "</span>");
                }
            }
        }.bind(this));
    }
};