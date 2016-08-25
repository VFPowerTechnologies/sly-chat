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

        userSessionController.startUserSession(e.accountInfo, e.plubicKey);
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
    }
};