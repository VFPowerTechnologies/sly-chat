var LoginController = function () {
    this.email = '';
    this.password = '';
};

LoginController.ids = {
    loginForm : "#loginForm",
    loginPasswordInput : "#login-psw",
    loginUsernameInput : "#login",
    loginRememberMeInput : "#rememberMe"
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
        userSessionController.clearUserSession();

        navigationController.loadPage("login.html", false);
        navigationController.clearHistory();
    },

    isLoggedIn : function () {
        return profileController.name !== '';
    },

    onLoginSuccessful : function (e) {
        slychat.hidePreloader();
        this.resetLoginInfo();

        userSessionController.startUserSession(e.accountInfo, e.publicKey);
        navigationController.loadInitialPage();
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
                $(LoginController.ids.loginPasswordInput).val("");
                $(LoginController.ids.loginForm).find(".error-block").html("<li>" + errorMessage +"</li>");
            }
        }
        else {
            this.resetLoginInfo();
            $(LoginController.ids.loginForm).find(".error-block").html("<li>An unexpected error occurred</li>");
            exceptionController.handleError(e);
        }
    },

    login : function (rememberMe) {
        var valid = slychat.validateForm($(LoginController.ids.loginForm));
        
        if(!valid)
            return;

        if(typeof rememberMe === "undefined")
            rememberMe = $(LoginController.ids.loginRememberMeInput).is(':checked');

        var username = $$(LoginController.ids.loginUsernameInput).val();
        var password = $$(LoginController.ids.loginPasswordInput).val();


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
    }
};