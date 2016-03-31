var LoginController = function(model){
    this.model = model;
};

LoginController.prototype = {
    init : function() {
        $(document).on("click", "#submitLoginBtn", function(e){
            e.preventDefault();
            $("#submitLoginBtn").prop("disabled", true);
            this.model.setItems({
                "login" : $("#login").val().replace(/\s+/g, ''),
                "password" : $("#login-psw").val()
            });

            if(this.model.validate() == true){
                this.login();
                $("#statusModal").openModal({
                    dismissible: false
                });
            }
            else{
                $("#submitLoginBtn").prop("disabled", false);
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
            KEYTAP.navigationController.clearHistory();
            $("#statusModal").closeModal();

            //if(initialPage === null)
            KEYTAP.navigationController.loadPage('contacts.html');
            //else
            //    KEYTAP.navigationController.goTo(initialPage);
        }.bind(this));
    },
    onLoginFailure : function(ev) {
        var errorMessage = ev.errorMessage;
        if(errorMessage !== null) {
            if(errorMessage == "Phone confirmation needed") {
                $("#statusModal").closeModal();
                KEYTAP.registrationController.model.setItems({"email" : this.model.getLogin(), "password" : this.model.getPassword()});
                KEYTAP.navigationController.loadPage("smsVerification.html");
            }
            else {
                document.getElementById("login-error").innerHTML = "<li>An error occurred: " + errorMessage + "</li>";
                $("#submitLoginBtn").prop("disabled", false);
                $("#statusModal").closeModal();
            }
        }
        else {
            $("#statusModal").closeModal();
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
        $(".menu-hidden").hide();
        $(".nav-btn").hide();
    }
};
