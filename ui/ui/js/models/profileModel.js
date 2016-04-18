var ProfileModel = function () {
    this.username = "";
    this.phoneNumber = "";
    this.name = "";
};

ProfileModel.prototype = {
    setUserInfo : function (username, phoneNumber, name) {
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.name = name;
    }
};