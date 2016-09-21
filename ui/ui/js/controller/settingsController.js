var SettingsController = function () {
    this.notificationConfig = null;
};

//TODO prevent user from editing until we've received the initial config
SettingsController.prototype = {
    init : function () {
        this.addConfigListeners();
    },

    addConfigListeners : function () {
        configService.addNotificationConfigChangeListener(function (newConfig) {
            console.log('Notification updated to: ' + JSON.stringify(newConfig, null, 2));
            this.notificationConfig = newConfig;
            this.refreshNotificationConfig();
        }.bind(this));
    },

    refreshNotificationConfig : function () {
        var c = this.notificationConfig;

        $('#notifications-enabled').prop('checked', c.enabled);
    },

    setNotificationsEnabled : function(isEnabled) {
        this.notificationConfig.enabled = isEnabled;

        configService.setNotificationConfig(this.notificationConfig).catch(function (e) {
            exceptionController.handleError(e);
        });
    }
};
