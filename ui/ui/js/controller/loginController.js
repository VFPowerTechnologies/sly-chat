var LoginController = function(model){
    this.model = model;
}

LoginController.prototype = {
    init : function() {
        var self = this;
        $("#loginBtn").click(function(e){
            e.preventDefault();
            self.model.setItems({
                "login" : $("#login").val(),
                "password" : $("#login-psw").val()
            });

            if(self.model.validate() == true){
                self.model.login();
            }
        });
    }
}