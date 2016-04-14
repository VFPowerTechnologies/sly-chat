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
        this.model.setUserInfo(userInfo.email, userInfo["phone-number"], userInfo.name);
    },
    clearCache : function () {
        this.model.setUserInfo('', '', '')
    }
};