var LoginModel = function(){
    this.items = {};
    this.login = '';
    this.password = '';
};

LoginModel.prototype = {
    setItems : function(items){
        this.items = items;
        this.login = items.login;
        this.password = items.password;
    },
    getPassword : function() {
        return this.password;
    },
    getLogin : function() {
        return this.login;
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
    },
    clearCache : function () {
        this.items = {};
        this.login = '';
        this.password = '';
    }
};