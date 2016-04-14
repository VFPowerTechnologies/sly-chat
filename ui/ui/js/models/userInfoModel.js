var UserInfoModel = function () {
    this.username = "";
    this.phoneNumber = "";
    this.name = "";
};

UserInfoModel.prototype = {
    setUserInfo : function (username, phoneNumber, name) {
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.name = name;
    }
};