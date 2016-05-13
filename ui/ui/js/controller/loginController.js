var LoginController = function(model){
    this.model = model;
    this.modal = this.createLoginModal();
};

LoginController.prototype = {
    init : function() {
        $(document).on("click", "#submitLoginBtn", function(e){
            e.preventDefault();

            var loginBtn = $("#submitLoginBtn");

            loginBtn.prop("disabled", true);

            this.setInfo($("#login").val().replace(/\s+/g, ''), $("#login-psw").val());

            if(this.model.validate() == true){
                this.login();
                this.modal.open();
            }
            else{
                loginBtn.prop("disabled", false);
            }
        }.bind(this));

        $(document).on("click", "#registrationGoBtn", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("register.html");
        });

        loginService.addLoginEventListener(this.onLoginEvent.bind(this));
    },
    onLoginEvent : function(ev) {
        switch(ev.state) {
            case "LOGGED_OUT":
                this.onLogout();
                break;

            case "LOGGING_IN":
                break;

            case "LOGGED_IN":
                this.onLoginSuccessful(ev);
                break;

            case "LOGIN_FAILED":
                this.onLoginFailure(ev);
                break;
        }
    },
    onLoginSuccessful : function(ev) {
        var accountInfo = ev.accountInfo;
        stateService.getInitialPage().then(function (initialPage) {
            KEYTAP.profileController.setUserInfo(accountInfo);
            $(".menu-hidden").show();
            this.modal.close();

            if(initialPage === null) {
                KEYTAP.navigationController.loadPage('contacts.html');
                KEYTAP.navigationController.clearHistory();
            }
            else
            {
                KEYTAP.navigationController.goTo(initialPage);
            }
        }.bind(this));
    },
    onLoginFailure : function(ev) {
        var errorMessage = ev.errorMessage;
        if(errorMessage !== null) {
            if(errorMessage == "Phone confirmation needed") {
                this.modal.close();
                KEYTAP.registrationController.model.setItems({"email" : this.model.getLogin(), "password" : this.model.getPassword()});
                KEYTAP.navigationController.loadPage("smsVerification.html");
            }
            else {
                this.setInfo("", "");
                document.getElementById("login-error").innerHTML = "<li>An error occurred: " + errorMessage + "</li>";
                $("#submitLoginBtn").prop("disabled", false);
                this.modal.close();
            }
        }
        else {
            this.setInfo("", "");
            this.modal.close();
            KEYTAP.exceptionController.displayDebugMessage(e);
            document.getElementById("login-error").innerHTML = "<li>An unexpected error occurred</li>";
            console.log("An unexpected error occured: " + e);
            $("#submitLoginBtn").prop("disabled", false);
        }
    },
    onLogout : function() {
        KEYTAP.navigationController.clearHistory();
        KEYTAP.contactController.model.resetContacts();
        KEYTAP.profileController.setUserInfo({
            "name" : "",
            "phone-number" : "",
            "email" : ""
        });
        this.model.setItems({
            "login" : "",
            "password" : ""
        });
        //do this last, so that everything's been reset before loading a new page
        KEYTAP.navigationController.loadPage("login.html");
    },
    login : function(rememberMe) {
        if(typeof rememberMe === "undefined")
            rememberMe = $("#rememberMe").is(':checked');

        loginService.login(this.model.getLogin(), this.model.getPassword(), rememberMe);
    },
    setInfo : function(login, password) {
        this.model.setItems({
            "login" : login,
            "password" : password
        })
    },
    logout : function () {
        loginService.logout().then(function () {
            this.clearUiCacheOnLogout();
        }.bind(this));
    },
    clearCache : function () {
        this.model.clearCache();
    },
    createLoginModal: function () {
        var html = "<div style='text-align: center;'> <h6 style='margin-bottom: 15px; color: whitesmoke;'>We are logging you in</h6> <i class='fa fa-spinner fa-3x fa-spin'></i> </div>";
        return createStatusModal(html);
    },
    clearUiCacheOnLogout : function () {
        KEYTAP.chatController.clearCache();
        KEYTAP.contactController.clearCache();
        this.clearCache();
        KEYTAP.profileController.clearCache();
        KEYTAP.registrationController.clearCache();
    }
};
