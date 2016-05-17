var ProfileModel = function () {
    this.username = "";
    this.phoneNumber = "";
    this.name = "";
    this.publicKey = "";
};

ProfileModel.prototype = {
    setUserInfo : function (username, phoneNumber, name, publicKey) {
        this.username = username;
        this.phoneNumber = phoneNumber;
        this.name = name;
        this.publicKey = publicKey;
    },
    setName : function (name) {
        this.name = name;
    },
    setUsername : function (username) {
        this.username = username;
    },
    setPhoneNumber : function (phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
};