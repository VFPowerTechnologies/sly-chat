var LoginController = function () {
    this.email = '';
    this.password = '';
    this.loggedIn = false;
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
        this.loggedIn = false;
        this.loadInitialPage();
        userSessionController.clearUserSession();
        navigationController.clearHistory();
    },

    isLoggedIn : function () {
        return this.loggedIn;
    },

    onLoginSuccessful : function (e) {
        this.loggedIn = true;
        slychat.hidePreloader();
        this.resetLoginInfo();

        userSessionController.startUserSession(e.accountInfo, e.publicKey);
        navigationController.loadInitialPage();
    },

    onLoginFailure : function (e) {
        this.loggedIn = false;
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

    loadInitialPage : function () {
        loginService.areAccountsPresent().then(function (present) {
            if (present)
                navigationController.loadPage("login.html", false);
            else {
                if (isDesktop)
                    navigationController.loadPage("register.html", false);
                else
                    navigationController.loadPage("registerStepOne.html", false);
            }
        }).catch(function (e) {
            exceptionController.handleError(e);
        })
    },

    resetLoginInfo : function () {
        this.email = '';
        this.password = '';
    }
};