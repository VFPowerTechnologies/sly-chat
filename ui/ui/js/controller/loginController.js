var LoginController = function () {
    this.email = '';
    this.password = '';
};

LoginController.prototype = {
    init : function () {
        loginService.addLoginEventListener(this.onLoginEvent.bind(this));
    },

    onLoginEvent : function (e) {
        switch(e.state) {
            case "LOGGED_OUT":
                this.onLogout();
                break;

            case "LOGGING_IN":
                break;

            case "LOGGED_IN":
                this.onLoginSuccessful(e);
                break;

            case "LOGIN_FAILED":
                this.onLoginFailure(e);
                break;
        }
    },

    onLogout : function () {
        this.resetLoginInfo();
        this.resetUiOnLogout();
        firstLogin = true;
        navigationController.loadPage("login.html", false);
        navigationController.clearHistory();
    },

    isLoggedIn : function () {
        return profileController.name !== '';
    },

    onLoginSuccessful : function (e) {
        slychat.hidePreloader();
        var accountInfo = e.accountInfo;
        var publicKey = e.publicKey;

        profileController.setUserInfo(accountInfo, publicKey);
        $("#leftDesktopProfileName").html(accountInfo.name);
        this.resetLoginInfo();

        var noStateLoad = ["register.html", "login.html", "smsVerification.html", "updatePhone.html"];

        stateService.getInitialPage().then(function (initialPage) {
            if(initialPage === null) {
                stateService.getState().then(function (state) {
                    if (state === null || state.currentPage === undefined || state.currentPage === null || state.currentPage == "login.html") {
                        navigationController.loadPage('contacts.html');
                        navigationController.clearHistory();
                    }
                    else {
                        if(state.currentPage.indexOf("chat.html") <= -1) {
                            if ($.inArray(state.currentPage, noStateLoad) > -1) {
                                navigationController.loadPage('contacts.html');
                                navigationController.clearHistory();
                            }
                            else {
                                navigationController.loadPage(state.currentPage, false);
                            }
                        }
                        else {
                            if (typeof state.currentContact != "undefined" && state.currentContact != null) {
                                contactController.fetchAndLoadChat(state.currentContact);
                            }
                            else {
                                navigationController.loadPage(state.currentPage, false);
                            }
                        }
                    }
                }).catch(function (e) {
                    exceptionController.handleError(e);
                });
            }
            else
            {
                navigationController.goTo(initialPage);
            }
        });
    },

    onLoginFailure : function (e) {
        slychat.hidePreloader();
        var errorMessage = e.errorMessage;
        if(errorMessage !== null) {
            if(errorMessage == "Phone confirmation needed") {
                var options = {
                    url : 'smsVerification.html',
                    query: {
                        password: this.password,
                        email: this.email
                    }
                };
                navigationController.loadPage("smsVerification.html", false, options);
            }
            else {
                this.resetLoginInfo();
                $("#login-psw").val("");
                $("#loginForm").find(".error-block").html("<li>" + errorMessage +"</li>");
            }
        }
        else {
            this.resetLoginInfo();
            $("#loginForm").find(".error-block").html("<li>An unexpected error occurred</li>");
            exceptionController.handleError(e);
        }
    },

    login : function (rememberMe) {
        var valid = slychat.validateForm($("#loginForm"));
        
        if(!valid)
            return;

        if(typeof rememberMe === "undefined")
            rememberMe = $("#rememberMe").is(':checked');

        var username = $$('#login').val();
        var password = $$('#login-psw').val();


        this.email = username;
        this.password = password;

        slychat.showPreloader();

        firstLogin = true;

        loginService.login(username, password, rememberMe);
    },

    logout : function () {
        loginService.logout();
    },

    resetLoginInfo : function () {
        this.email = '';
        this.password = '';
    },

    resetUiOnLogout : function () {
        groupController.clearCache();
        contactController.clearCache();
        connectionController.clearCache();
        chatController.clearCache();
        profileController.resetProfileInfo();
        registrationController.clearCache();

        $("#contact-list").html("");
        $("#recentContactList").html("");
        $("#groupList").html("");
    }
};