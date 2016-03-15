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

        $(document).on("click", "#addContactBtn", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("addContact.html");
        });
    }
};