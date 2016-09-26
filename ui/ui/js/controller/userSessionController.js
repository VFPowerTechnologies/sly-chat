var UserSessionController = function () {};

UserSessionController.prototype = {
    startUserSession : function (accountInfo, publicKey) {
        profileController.setUserInfo(accountInfo, publicKey);
        $("#leftDesktopProfileName").html(accountInfo.name);
        $("#leftMenuUserInfo").html(accountInfo.name);
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
        $("#contact-list").html("");
        $("#recentContactList").html("");
        $("#groupList").html("");
        $("#leftContactList").html("");
        $("#leftGroupList").html("");
        $("#leftMenuUserInfo").html("");
    }
};