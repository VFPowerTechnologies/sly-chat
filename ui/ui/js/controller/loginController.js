var LoginController = function(model){
    this.model = model;
}

LoginController.prototype = {
    init : function() {
        $("#loginBtn").click(function(e){
            e.preventDefault();
            this.model.setItems({
                "login" : $("#login").val(),
                "password" : $("#login-psw").val()
            });

            if(this.model.validate() == true){
                this.login();
            }
        }.bind(this));

        $("#registrationBtn").click(function (e) {
            e.preventDefault();
            KEYTAP.navigationController.loadPage("register.html");
        });
    },
    login : function() {
        loginService.login(this.model.getLogin(), this.model.getPassword()).then(function () {
            $(".menu-hidden").show();
            KEYTAP.navigationController.loadPage('contacts.html');
            KEYTAP.navigationController.clearHistory();
        }.bind(this)).catch(function (e) {
            document.getElementById("login-error").innerHTML = "<li>An error occurred</li>";
//            document.getElementById("login-error").innerHTML = "<li>Wrong email or password</li>";
        });
    },
    logout : function () {
        KEYTAP.navigationController.clearHistory();
        $(".menu-hidden").hide();
        $(".nav-btn").hide();
        KEYTAP.navigationController.loadPage("index.html");
    }
}