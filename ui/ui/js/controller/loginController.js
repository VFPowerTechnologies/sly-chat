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

            this.model.setItems({
                "login" : $("#login").val().replace(/\s+/g, ''),
                "password" : $("#login-psw").val()
            });

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
    },
    registerForLoginEvents: function() {
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
            KEYTAP.userInfoController.setUserInfo(accountInfo);
            if($("#rememberMe").is(':checked')) {
                window.configService.setStartupInfo({lastLoggedInAccount: this.model.getLogin(), savedAccountPassword: this.model.getPassword()}).then(function () {
                    console.log('Wrote startup info');
                }).catch(function (e) {
                    KEYTAP.exceptionController.displayDebugMessage(e);
                    console.log(e);
                });
            }
            $(".menu-hidden").show();
            this.modal.close();

            console.log(initialPage);

            if(initialPage === null) {
                KEYTAP.navigationController.loadPage('contacts.html');
                KEYTAP.navigationController.clearHistory();
            }
            else
            {
                KEYTAP.navigationController.goTo(initialPage);
                KEYTAP.navigationController.clearHistory();
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
                document.getElementById("login-error").innerHTML = "<li>An error occurred: " + errorMessage + "</li>";
                $("#submitLoginBtn").prop("disabled", false);
                this.modal.close();
            }
        }
        else {
            this.modal.close();
            KEYTAP.exceptionController.displayDebugMessage(e);
            document.getElementById("login-error").innerHTML = "<li>An unexpected error occurred</li>";
            console.log("An unexpected error occured: " + e);
            $("#submitLoginBtn").prop("disabled", false);
        }
    },
    onLogout : function() {
        KEYTAP.navigationController.loadPage("login.html");
        KEYTAP.navigationController.clearHistory();
        KEYTAP.contactController.model.resetContacts();
        KEYTAP.userInfoController.setUserInfo({
            "name" : "",
            "phone-number" : "",
            "email" : ""
        });
    },
    login : function() {
        loginService.login(this.model.getLogin(), this.model.getPassword());
    },
    setInfo : function(login, password) {
        this.model.setItems({
            "login" : login,
            "password" : password
        })
    },
    logout : function () {
        window.configService.setStartupInfo({lastLoggedInAccount: "", savedAccountPassword: null}).then(function () {
            console.log('Cleared saved info on logout');
        }).catch(function (e) {
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log(e);
        });
        loginService.logout();
    },
    createLoginModal: function () {
        var html = "<div style='text-align: center;'> <h6 style='margin-bottom: 15px; color: whitesmoke;'>We are logging you in</h6> <i class='fa fa-spinner fa-3x fa-spin'></i> </div>";
        return createStatusModal(html);
    }
};
