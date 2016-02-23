var ChatModel = function () {};

ChatModel.prototype = {
    setController : function (controller) {
        this.controller = controller;
    },
    fetchMessage : function (start, count, contact) {
        messengerService.getLastMessagesFor(contact, start, count).then(function (messages) {
            this.controller.displayMessage(messages, contact);
        }.bind(this)).catch(function (e) {
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.error("Unable to fetch messages: " + e);
        });
    }
};