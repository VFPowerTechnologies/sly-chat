var ProfileController = function (model) {
    this.model = model;
};

ProfileController.prototype = {
    init : function() {
        var userInfo = this.getUserInfo();

        $("#profileAvatar").html("<a href='#'>" + createAvatar(userInfo.name, "#fff", "#212121") + "</a>");

        $("#profileNameDisplay").html(userInfo.name);

        $("#profileEmailDisplay").html(userInfo.username);

        $("#updateEmailForm").submit(this.updateEmail.bind(this));
        $("#updatePhoneForm").submit(this.updatePhone.bind(this));
        $("#updateNameForm").submit(this.updateName.bind(this));
    },
    getUserInfo: function () {
        return {
            "username" : this.model.username,
            "phoneNumber" : this.model.phoneNumber,
            "name" : this.model.name
        }
    },
    setUserInfo: function (userInfo) {
        this.model.setUserInfo(userInfo.email, userInfo["phone-number"], userInfo.name);
    },
    clearCache : function () {
        this.model.setUserInfo('', '', '')
    },
    updatePhone : function (e) {
        e.preventDefault();
        var formValid = validateForm("#updatePhoneForm");
        var phoneValid = validatePhone();

        if(formValid == true && phoneValid == true) {
            var phone = $("#phone").val();
        }
    },
    updateEmail : function (e) {
        e.preventDefault();
        if(validateForm("#updateEmailForm")) {
            var email = $("#profileEmail").val();
        }
    },
    updateName : function (e) {
        e.preventDefault();
        if(validateForm("#updateNameForm")) {
            var name = $("#profileName").val();
        }
    }
};