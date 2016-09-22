var SettingsController = function () {
    this.notificationConfig = null;
};

SettingsController.ids = {
    notificationsEnabled : '#notifications-enabled-checkbox',
    notificationsSound : '#notification-sound-select-btn'
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

        $(SettingsController.ids.notificationsEnabled).prop('checked', c.enabled);
    },

    updateNotificationConfig : function () {
        configService.setNotificationConfig(this.notificationConfig).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    setNotificationsEnabled : function (isEnabled) {
        this.notificationConfig.enabled = isEnabled;

        this.updateNotificationConfig();
    },

    setNotificationSound : function (sound) {
        console.log('Notification sound: ' + sound);

        this.notificationConfig.sound = sound;

        this.updateNotificationConfig();
    },

    selectNotificationSound : function () {
        windowService.selectNotificationSound(this.notificationConfig.sound).then(function (sound) {
            this.setNotificationSound(sound);
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    displaySettings : function () {
        this.refreshNotificationConfig();
    },

    initEventHandlers : function () {
        $(SettingsController.ids.notificationsEnabled).on('change', function (e) {
            e.preventDefault();

            settingsController.setNotificationsEnabled(e.target.checked);
        });

        $(SettingsController.ids.notificationsSound).on('click', function (e) {
            settingsController.selectNotificationSound();
        });
    },

    onPageInit : function () {
        settingsController.initEventHandlers();
        settingsController.displaySettings();
    }
};
