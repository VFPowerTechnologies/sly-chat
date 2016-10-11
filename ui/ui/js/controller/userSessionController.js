var UserSessionController = function () {};

UserSessionController.ids = {
    contactList : '#contact-list',
    recentContactList : '#recentContactList',
    grouplist : '#groupList',
    leftContactList : '#leftContactList',
    leftGroupList : '#leftGroupList',
    leftMenuUserInfo : '#leftMenuUserInfo',
    leftMenuProfileName : '#leftMenuProfileName'
};

UserSessionController.prototype = {
    startUserSession : function (accountInfo, publicKey) {
        profileController.setUserInfo(accountInfo, publicKey);
        $(UserSessionController.ids.leftMenuProfileName).html(accountInfo.name);
        $(UserSessionController.ids.leftMenuUserInfo).html(accountInfo.name);
    },

    clearUserSession : function () {
        this.clearUICache();
        this.clearUIContent();
    },

    clearUICache : function () {
        loginController.resetLoginInfo();
        window.firstLogin = true;
        groupController.clearCache();
        contactController.clearCache();
        connectionController.clearCache();
        chatController.clearCache();
        profileController.resetProfileInfo();
        registrationController.clearCache();
    },

    clearUIContent : function () {
        $(UserSessionController.ids.contactList).html("");
        $(UserSessionController.ids.recentContactList).html("");
        $(UserSessionController.ids.grouplist).html("");
        $(UserSessionController.ids.leftContactList).html("");
        $(UserSessionController.ids.leftGroupList).html("");
        $(UserSessionController.ids.leftMenuUserInfo).html("");
        $(UserSessionController.ids.leftMenuProfileName).html("");
    }
};