var LoginController = function(model){
    this.model = model;
};

LoginController.prototype = {
    init : function() {
        $(document).on("click", "#submitLoginBtn", function(e){
            e.preventDefault();
            $("#submitLoginBtn").prop("disabled", true);
            this.model.setItems({
                "login" : $("#login").val(),
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
    login : function() {
        loginService.login(this.model.getLogin(), this.model.getPassword()).then(function (result) {
            if (result.successful) {
                if($("#rememberMe").is(':checked')) {
                    window.configService.setStartupInfo({lastLoggedInAccount: this.model.getLogin(), savedAccountPassword: this.model.getPassword()}).then(function () {
                        console.log('Wrote startup info');
                    }).catch(function (e) {
                        KEYTAP.exceptionController.displayDebugMessage(e);
                        console.log(e);
                    });
                }
                $(".menu-hidden").show();
                KEYTAP.navigationController.loadPage('contacts.html');
                KEYTAP.navigationController.clearHistory();
                $("#statusModal").closeModal();
            }
            else if(result.errorMessage == "Phone confirmation needed") {
                $("#statusModal").closeModal();
                KEYTAP.registrationController.model.setItems({"email" : this.model.getLogin(), "password" : this.model.getPassword()});
                KEYTAP.navigationController.loadPage("smsVerification.html");
            }
            else {
                document.getElementById("login-error").innerHTML = "<li>An error occurred: " + result.errorMessage + "</li>";
                $("#submitLoginBtn").prop("disabled", false);
                $("#statusModal").closeModal();
            }
        }.bind(this)).catch(function (e) {
            $("#statusModal").closeModal();
            KEYTAP.exceptionController.displayDebugMessage(e);
            document.getElementById("login-error").innerHTML = "<li>An unexpected error occurred</li>";
            console.log("An unexpected error occured: " + e);
            $("#submitLoginBtn").prop("disabled", false);
        });
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
        KEYTAP.navigationController.loadPage("login.html");
        KEYTAP.navigationController.clearHistory();
        KEYTAP.contactController.model.resetContacts();
    }
};