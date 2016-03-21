var UserInfoController = function (model) {
    this.model = model;
};

UserInfoController.prototype = {
    getUserInfo: function () {
        return {
            "username" : this.model.username,
            "phoneNumber" : this.model.phoneNumber,
            "name" : this.model.name
        }
    },
    setUserInfo: function (userInfo) {
        this.model.username = userInfo.email;
        this.model.phoneNumber = userInfo["phone-number"];
        this.model.name = userInfo.name;
    }
};