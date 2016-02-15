var ContactModel = function () {
    this.conversations = [];
    this.currentContact = null;
}

ContactModel.prototype = {
    fetchConversation : function () {
        messengerService.getConversations().then(function(conversations){
            conversations.forEach(function(conversation){
                this.conversations[conversation.contact.id] = conversation;
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
    setCurrentContact : function (id) {
        this.currentContact = this.conversations[id].contact;
    },
    getCurrentContact : function () {
        return this.currentContact;
    },
    getContact : function (id) {
        return this.conversations[id].contact;
    },
    validateNewContact : function () {
        var validation = $("#addContactForm").parsley({
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
    }
}