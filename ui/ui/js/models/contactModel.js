var ContactModel = function () {
    this.conversations = [];
    this.currentContact = null;
};

ContactModel.prototype = {
    fetchConversation : function () {
        messengerService.getConversations().then(function(conversations){
            KEYTAP.recentChatController.init(conversations);
            var forContact = this.orderByName(conversations);

            forContact.forEach(function(conversation){
                this.conversations[conversation.contact.email] = conversation;
            }.bind(this));

            this.controller.displayContacts(this.conversations);
        }.bind(this)).catch(function(e){
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log("Unable to fetch conversations: " + e);
        });
    },
    getConversations : function () {
        return this.conversations;
    },
    resetContacts : function () {
        this.conversations = [];
    },
    setController : function (controller) {
        this.controller = controller;
    },
    setCurrentContact : function (email) {
        this.currentContact = this.conversations[email].contact;
    },
    getCurrentContact : function () {
        return this.currentContact;
    },
    getContact : function (email) {
        var conversation = this.conversations[email];
        if (!conversation)
            return null;
        else
            return conversation.contact;
    },
    validateContact : function (formId) {
        var validation = $(formId).parsley({
            errorClass: "invalid",
            focus: 'none',
            errorsWrapper: '<div class="pull-right parsley-errors-list" style="color: red;"></div>',
            errorTemplate: '<p></p>'
        });
        var isValid = validation.validate();

        if(isValid == true){
            return true;
        }
        else{
            return false;
        }
    },
    fetchConversationForChat : function (email, pushCurrentPage) {
        messengerService.getConversations().then(function(conversations){
            conversations.forEach(function(conversation){
                KEYTAP.contactController.model.conversations[conversation.contact.email] = conversation;
            });
            KEYTAP.contactController.model.setCurrentContact(email);
            KEYTAP.navigationController.loadPage("chat.html", pushCurrentPage);
        }.bind(email)).catch(function(e){
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log("Unable to fetch conversations: " + e);
        });
    },
    orderByName : function (conversations) {
        conversations.sort(function(a, b) {
            var emailA = a.contact.email.toLowerCase();
            var emailB = b.contact.email.toLowerCase();

            if(emailA < emailB)
                return -1;
            if(emailA > emailB)
                return 1;

            return 0;
        });

        return conversations;
    }
};
