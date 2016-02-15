var LoginController = function(model){
    this.model = model;
}

LoginController.prototype = {
    init : function() {
        $("#loginBtn").click(function(e){
            e.preventDefault();
            $("#loginBtn").prop("disabled", true);
            this.model.setItems({
                "login" : $("#login").val(),
                "password" : $("#login-psw").val()
            });

            if(this.model.validate() == true){
                this.login();
            }
            else{
                $("#loginBtn").prop("disabled", false);
            }
        }.bind(this));

        $("#registrationBtn").click(function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("register.html");
        });
    },
    login : function() {
        loginService.login(this.model.getLogin(), this.model.getPassword()).then(function (result) {
            if (result.successful) {
                $(".menu-hidden").show();
                KEYTAP.navigationController.loadPage('contacts.html');
                KEYTAP.navigationController.clearHistory();
            }
            else {
                document.getElementById("login-error").innerHTML = "<li>An error occurred: " + result.errorMessage + "</li>";
                $("#loginBtn").prop("disabled", false);
            }
        }.bind(this)).catch(function (e) {
            document.getElementById("login-error").innerHTML = "<li>An unexpected error occurred</li>";
            $("#loginBtn").prop("disabled", false);
//            document.getElementById("login-error").innerHTML = "<li>Wrong email or password</li>";
        });
    },
    setInfo : function(login, password) {
        this.model.setItems({
            "login" : login,
            "password" : password
        })
    },
    logout : function () {
        KEYTAP.navigationController.clearHistory();
        $(".menu-hidden").hide();
        $(".nav-btn").hide();
        KEYTAP.navigationController.loadPage("index.html");
    }
}