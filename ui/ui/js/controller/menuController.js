var MenuController = function () {};

MenuController.prototype = {
    init : function () {
        $(document).on("click", "#backBtn", function (e) {
            e.preventDefault();
            windowService.closeSoftKeyboard().then(function () {
                KEYTAP.navigationController.goBack();
            }).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.log("An error occured while going back");
            });
        });

        $(document).on("click", "#logoutBtn", function (e) {
            e.preventDefault();
            KEYTAP.loginController.logout();
        });

        $(document).on("click", "#addContactBtn", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("addContact.html", true);
        });

        $(document).on("click", "#editProfileBtn", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("profile.html", true);
        });
    },
    handleMenuDisplay : function() {
        // handle the connection status display on each page load
        KEYTAP.connectionController.handleConnectionDisplay();

        var currentUrl = window.location.href;
        var page = currentUrl.substring(currentUrl.lastIndexOf("/") + 1);

        var editProfileBtn = $("#editProfileBtn");
        var addContactBtn = $("#addContactBtn");

        if(page == "chat.html" && (KEYTAP.connectionController.relayConnected == false || KEYTAP.connectionController.networkAvailable == false))
            $("#newMessageSubmitBtn").prop("disabled", true);
        else
            $("#newMessageSubmitBtn").prop("disabled", false);

        switch (page) {
            case "addContact.html":
                editProfileBtn.prop("disabled", false);
                editProfileBtn.parent("li").removeClass("disabled");

                addContactBtn.prop("disabled", true);
                addContactBtn.parent("li").addClass("disabled");

                break;
            case "profile.html":
                editProfileBtn.prop("disabled", true);
                editProfileBtn.parent("li").addClass("disabled");

                addContactBtn.prop("disabled", false);
                addContactBtn.parent("li").removeClass("disabled");

                break;
            default:
                editProfileBtn.prop("disabled", false);
                editProfileBtn.parent("li").removeClass("disabled");

                addContactBtn.prop("disabled", false);
                addContactBtn.parent("li").removeClass("disabled");
        }

        var notifContainer = $('[data-notify="container"]');
        switch (page) {
            case "login.html":
            case "register.html":
            case "updatePhone.html":
            case "smsVerification.html":
            case "index.html":
                notifContainer.hide();
                break;
            default:
                notifContainer.show();
                break;
        }
    }
};