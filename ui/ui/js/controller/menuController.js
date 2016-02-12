var MenuController = function () {

}

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

            develService.receiveFakeMessage(KEYTAP.contactController.getContact(0), "Fake").catch(function (e) {
                console.log('receiveFakeMessage failed: ' + e);
            });
        });

        $(document).on("click", "#addContactBtn", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("addContact.html");
        });
    }
}