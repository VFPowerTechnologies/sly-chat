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
    },
    setMenuItems : function () {
        this.contactTabs = $("#contactTabs");
        this.backBtn = $("#nav-menu-back");
        this.logoutBtn = $("#nav-menu-logout");
        this.title = $("#page-title");
        this.networkStatus = $("#networkStatus");
        this.addContactBtn = $("#addContactMenu");
    },
    handleMenuDisplay : function() {
        var currentUrl = window.location.href;
        var page = currentUrl.substring(currentUrl.lastIndexOf("/") + 1);

        console.log(page);
        console.log(this.title.html());

        switch (page) {
            case "login.html":
                this.title.html("Login");
                this.title.show();
                this.networkStatus.hide();
                this.backBtn.hide();
                this.logoutBtn.hide();
                this.contactTabs.hide();
                this.addContactBtn.hide();

                break;
            case "register.html":
                this.title.html("Register");
                this.title.show();
                this.networkStatus.hide();
                this.backBtn.hide();
                this.logoutBtn.hide();
                this.contactTabs.hide();
                this.addContactBtn.hide();

                break;
            case "contacts.html":
                this.backBtn.hide();
                this.logoutBtn.show();
                this.title.html("");
                this.title.hide();
                this.contactTabs.show();
                this.addContactBtn.show();

                break;
            case "chat.html":
                var currentContact = KEYTAP.contactController.getCurrentContact();

                this.backBtn.show();
                this.logoutBtn.hide();
                this.title.html(currentContact.name);
                this.title.show();
                this.contactTabs.hide();
                this.addContactBtn.hide();

                break;
            case "addContact.html":
                this.backBtn.show();
                this.logoutBtn.hide();
                this.title.html("Add Contact");
                this.title.show();
                this.contactTabs.hide();
                this.addContactBtn.hide();

                break;
            case "smsVerification.html":
                this.title.html("Phone Verification");
                this.title.show();
                this.backBtn.hide();
                this.logoutBtn.hide();
                this.contactTabs.hide();
                this.addContactBtn.hide();

                break;
            case "updatePhone.html":
                this.title.html("Phone Update");
                this.title.show();
                this.backBtn.hide();
                this.logoutBtn.hide();
                this.contactTabs.hide();
                this.addContactBtn.hide();

                break;
        }
    }
};