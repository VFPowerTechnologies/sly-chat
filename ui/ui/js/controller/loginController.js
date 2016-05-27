var LoginController = function(model){
    this.model = model;
    this.modal = this.createLoginModal();
};

LoginController.prototype = {
    /**
     * Init function, ran only once when login controller is initiated.
     */
    init : function() {
        /**
         * Click event for submit login button.
         */
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

        /**
         * Click event for go to registration button.
         */
        $(document).on("click", "#registrationGoBtn", function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("register.html");
        });

        loginService.addLoginEventListener(this.onLoginEvent.bind(this));
    },
    /**
     * Login event handler.
     *
     * @param ev
     */
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
    /**
     * Handle login successful event.
     *
     * @param ev
     */
    onLoginSuccessful : function(ev) {
        var accountInfo = ev.accountInfo;
        var publicKey = ev.publicKey;
        stateService.getInitialPage().then(function (initialPage) {
            KEYTAP.profileController.setUserInfo(accountInfo, publicKey);
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
    /**
     * Handle login failure event.
     *
     * @param ev
     */
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
            KEYTAP.navigationController.loadPage("login.html");
            this.setInfo("", "");
            this.modal.close();
            $("#login-error").html("<li>An unexpected error occurred</li>");
            console.log("An unexpected error occured: ");
            console.log(ev);
            $("#submitLoginBtn").prop("disabled", false);
        }
    },
    /**
     * Handle on logout event.
     */
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
    /**
     * Handle submiting login to the backend.
     *
     * @param rememberMe
     */
    login : function(rememberMe) {
        if(typeof rememberMe === "undefined")
            rememberMe = $("#rememberMe").is(':checked');

        loginService.login(this.model.getLogin(), this.model.getPassword(), rememberMe);
    },
    /**
     * Set login and password in cache to use during login process.
     * @param login
     * @param password
     */
    setInfo : function(login, password) {
        this.model.setItems({
            "login" : login,
            "password" : password
        })
    },
    /**
     * Handle login out with the backend.
     */
    logout : function () {
        loginService.logout().then(function () {
            this.clearUiCacheOnLogout();
        }.bind(this));
    },
    /**
     * Handle clearing the model cache.
     */
    clearCache : function () {
        this.model.clearCache();
    },
    /**
     * Create the login status dialog.
     *
     * @returns {*}
     */
    createLoginModal: function () {
        var html = "<div style='text-align: center;'> <h6 style='margin-bottom: 15px; color: whitesmoke;'>We are logging you in</h6> <i class='fa fa-spinner fa-3x fa-spin'></i> </div>";
        return createStatusModal(html);
    },
    /**
     * Handle clearing the whole UI cache on logout.
     */
    clearUiCacheOnLogout : function () {
        KEYTAP.chatController.clearCache();
        KEYTAP.contactController.clearCache();
        this.clearCache();
        KEYTAP.profileController.clearCache();
        KEYTAP.registrationController.clearCache();
    }
};