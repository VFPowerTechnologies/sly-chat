var LoginModel = function(){
    var self = this;
};

LoginModel.prototype = {
    setItems : function(items){
        self.items = items;
        self.login = items.login;
        self.password = items.password;
    },
    getPassword : function() {
        return self.password;
    },
    getLogin : function() {
        return self.login;
    },
    validate : function(){
        var validation = $("#loginForm").parsley({
            errorClass: "invalid",
            focus: 'none',
            errorsWrapper: '<div class="pull-right parsley-errors-list" style="color: red;"></div>',
            errorTemplate: '<p></p>'
        });
        var isValid = validation.validate();

        if(isValid == true){
            return true;
        }
        else{
            return false;
        }
    }
}