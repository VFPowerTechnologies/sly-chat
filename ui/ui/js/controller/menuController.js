var MenuController = function () {};

MenuController.prototype = {
    init : function () {
        $(document).on("click", "#backBtn", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.goBack();
        });

        $(document).on("click", "#logoutBtn", function (e) {
            e.preventDefault();
            KEYTAP.loginController.logout();
        });

        //Send a fake message to test receive message listener.
        $(document).on("click", '#sendFakeMessage', function(e){
            e.preventDefault();

            var conversations = KEYTAP.contactController.getConversations();
            for(email in conversations) {
                if(conversations.hasOwnProperty(email)) {
                    develService.receiveFakeMessage(conversations[email].contact, "fake message to: " + conversations[email].contact.name).catch(function (e) {
                        KEYTAP.exceptionController.displayDebugMessage(e);
                        console.log('receiveFakeMessage failed: ' + e);
                    });
                }
            }
        });

        $(document).on("click", "#addContactBtn", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("addContact.html");
        });
    }
};