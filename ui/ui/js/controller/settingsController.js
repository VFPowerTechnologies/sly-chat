var SettingsController = function () {
    this.notificationConfig = null;
};

SettingsController.ids = {
    notificationsEnabled : '#notifications-enabled-checkbox',
    notificationsSound : '#notification-sound-select-btn',
    notificationSoundName : '#notification-sound-name',
    darkThemeCheckBox : '#darkThemeCheckBox'
};

//TODO prevent user from editing until we've received the initial config
SettingsController.prototype = {
    init : function () {
        this.addConfigListeners();
    },

    onPageInit : function () {
        settingsController.initEventHandlers();
        settingsController.displaySettings();
    },

    addConfigListeners : function () {
        configService.addNotificationConfigChangeListener(function (newConfig) {
            this.notificationConfig = newConfig;
            this.refreshNotificationConfig();
        }.bind(this));

        configService.addAppearanceConfigChangeListener(function (config) {
            this.themeConfig = config;
            this.refreshAppearanceConfig();
        }.bind(this));
    },

    refreshNotificationConfig : function () {
        var c = this.notificationConfig;
        var soundName = c.sound == null ? "No Notification Sound Selected" : c.soundName;

        $(SettingsController.ids.notificationsEnabled).prop('checked', c.enabled);
        $(SettingsController.ids.notificationSoundName).html(soundName);
    },

    refreshAppearanceConfig : function () {
        if (this.themeConfig.theme === null) {
            $(SettingsController.ids.darkThemeCheckBox).prop('checked', false);
        }
        else {
            $(SettingsController.ids.darkThemeCheckBox).prop('checked', true);
        }

        uiController.setAppTheme(this.themeConfig.theme);
    },

    updateNotificationConfig : function () {
        configService.setNotificationConfig(this.notificationConfig).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    setAppearanceConfig : function (checked) {
        if (checked) {
            this.themeConfig = {theme: "dark"};
            configService.setAppearanceConfig(this.themeConfig);
        }
        else {
            this.themeConfig = {theme: null};
            configService.setAppearanceConfig(this.themeConfig);
        }

        this.refreshAppearanceConfig();
    },

    setNotificationsEnabled : function (isEnabled) {
        this.notificationConfig.enabled = isEnabled;

        this.updateNotificationConfig();
    },

    setNotificationSound : function (sound) {
        this.notificationConfig.sound = sound;

        this.updateNotificationConfig();
    },

    selectNotificationSound : function () {
        windowService.selectNotificationSound(this.notificationConfig.sound).then(function (result) {
            if(result.ok)
                this.setNotificationSound(result.value);
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    displaySettings : function () {
        this.refreshNotificationConfig();
        this.refreshAppearanceConfig();
    },

    initEventHandlers : function () {
        $(SettingsController.ids.notificationsEnabled).on('change', function (e) {
            e.preventDefault();

            settingsController.setNotificationsEnabled(e.target.checked);
        });

        $(SettingsController.ids.notificationsSound).on('click', function (e) {
            settingsController.selectNotificationSound();
        });

        $(SettingsController.ids.darkThemeCheckBox).on('change', function (e) {
            e.preventDefault();
            settingsController.setAppearanceConfig(e.target.checked);
        });
    }
};
