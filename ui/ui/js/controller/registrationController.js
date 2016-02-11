var RegistrationController = function (model) {
    this.model = model;
}

RegistrationController.prototype = {
    init : function () {
        var self = this;
        $("#registerBtn").click(function(e){
            e.preventDefault();
            self.model.setItems({
                "name" : $("#name").val(),
                "password" : $("#password").val(),
                "email" : $("#email").val(),
                "phone" : $("#phone").val()
            });

            if(self.model.validate() == true){
                self.model.register();
            }
        });
    }
}