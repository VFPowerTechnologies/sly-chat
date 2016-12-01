var UIController = function () {
    window.registrationService = new RegistrationService();
    window.platformInfoService = new PlatformInfoService();
    window.messengerService = new MessengerService();
    window.loginService = new LoginService();
    window.contactService = new ContactsService();
    window.historyService = new HistoryService();
    window.networkStatusService = new NetworkStatusService();
    window.stateService = new StateService();
    window.telephonyService = new TelephonyService();
    window.windowService = new WindowService();
    window.eventService = new EventService();
    window.accountModifictationService = new AccountModificationService();
    window.platformService = new PlatformService();
    window.loadService = new LoadService();
    window.infoService = new InfoService();
    window.configService = new ConfigService();
    window.groupService = new GroupService();
    window.clientInfoService = new ClientInfoService();
    window.feedbackService = new FeedbackService();
    window.eventLogService = new EventLogService();
    window.shareService = new ShareService();

    window.navigationController = new NavigationController();
    window.userSessionController = new UserSessionController();
    window.loginController = new LoginController();
    window.registrationController = new RegistrationController();
    window.profileController = new ProfileController();
    window.contactController = new ContactController();
    window.chatController = new ChatController();
    window.connectionController = new ConnectionController();
    window.groupController = new GroupController();
    window.exceptionController = new ExceptionController();
    window.settingsController = new SettingsController();
    window.feedbackController = new FeedbackController();
    window.emojiController = new EmojiController();

    window.relayTimeDifference = 0;

    window.$$ = Dom7;
    $.fn.intlTelInput.loadUtils("js/external-lib/utils.js");
};

UIController.prototype = {
    startUI : function () {
        this.initApplication();
        this.initMainView();
        this.createMobileMenu();
        this.handlePlatformUpdate();
        this.initController();
        this.addTimeDifferenceListener();
        this.setSoftKeyboardInfoListener();
        this.addOutdatedVersionListener();
        this.count = 0;
    },

    setSoftKeyboardInfoListener : function () {
        windowService.setSoftKeyboardInfoListener(function (info) {
            if (typeof slychat.keyboardInfo === "undefined") {
                slychat.keyboardInfo = {
                    height: {
                        portrait: 0,
                        landscape: 0
                    },
                    window: {
                        portrait: {
                            height: 0,
                            width: 0
                        },
                        landscape: {
                            height: 0,
                            width: 0
                        }
                    },
                    container: {
                        portrait: 0,
                        landscape: 0
                    },
                    isVisible: info.isVisible
                };
            }


            slychat.keyboardInfo.isVisible = info.visible;
            if (info.visible) {
                if ($(".mobile-emoji-picker-opened").length > 0)
                    emojiController.closeMobileEmoji();

                var keyboardHeight;
                if (window.orientation === 0) {
                    if (slychat.keyboardInfo.height.portrait === 0) {
                        keyboardHeight = emojiController.getKeyboardPortraitHeight();
                        slychat.keyboardInfo.height.portrait = keyboardHeight;
                        slychat.keyboardInfo.container.portrait = emojiController.calcContainerSize(keyboardHeight, slychat.keyboardInfo.window.portrait.height);
                    }
                }
                else {
                    if (slychat.keyboardInfo.height.landscape === 0) {
                        keyboardHeight = emojiController.getKeyboardLandscapeHeight();
                        slychat.keyboardInfo.height.landscape = keyboardHeight;
                        slychat.keyboardInfo.container.landscape = emojiController.calcContainerSize(keyboardHeight, slychat.keyboardInfo.window.landscape.height);
                    }
                }
            }
            else {
                if (slychat.keyboardInfo.window.portrait.height === 0) {
                    if (Math.abs(window.orientation) === 0) {
                        slychat.keyboardInfo.window.portrait.height = window.innerHeight;
                        slychat.keyboardInfo.window.portrait.width = window.innerWidth;
                    }
                }
                if (slychat.keyboardInfo.window.landscape.height === 0) {
                    if (Math.abs(window.orientation) !== 0) {
                        slychat.keyboardInfo.window.landscape.height = window.innerHeight;
                        slychat.keyboardInfo.window.landscape.width = window.innerWidth;
                    }
                }
            }
        }).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    addTimeDifferenceListener : function () {
        messengerService.addClockDifferenceUpdateListener(function (difference) {
            window.relayTimeDifference = difference;
        }).catch(function (e) {
            exceptionController.handleError(e);
        })
    },

    addOutdatedVersionListener : function () {
        clientInfoService.addVersionOutdatedListener(function (result) {
            if (!result.latest)
                this.createOutOfDatePopup(result.latestVersion);
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    initController : function () {
        settingsController.init();
        navigationController.init();
        loginController.init();
        chatController.init();
        contactController.addContactEventListener();
        contactController.addConversationInfoUpdateListener();
        connectionController.init();
        groupController.addGroupEventListener();
        emojiController.setEmojione();
        emojiController.setWindowRotationListener();
    },

    initApplication : function () {
        window.isAndroid = Framework7.prototype.device.android === true;
        window.isIos = Framework7.prototype.device.ios === true;
        window.isDesktop = Framework7.prototype.device.ios === false && Framework7.prototype.device.android === false;

        Template7.global = {
            android: isAndroid,
            ios: isIos,
            isDesktop: isDesktop
        };

        window.firstLoad = true;
        window.firstLogin = true;

        window.slychat = new Framework7({
            desktop: isDesktop,
            material: !isIos,
            cache: false,
            swipeBackPage: false,
            tapHold: true,
            tapHoldDelay: 500,
            swipePanelOnlyClose: true,
            swipePanelNoFollow: true,
            modalTitle: 'Sly',
            template7Pages: true
        });
    },

    initMainView : function () {
        window.mainView = window.slychat.addView('.view-main', {
            dynamicNavbar: true,
            reloadPages: true
        });
    },

    createMobileMenu : function () {
        shareService.isSupported().then(function (isSupported) {
            window.shareSupported = isSupported;
            if (!isDesktop)
                navigationController.createMenu();
        }).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    handlePlatformUpdate : function () {
        if(!isIos)
            $("body").addClass("theme-deeporange");
        else
            $("body").addClass("theme-orange");

        if (!isIos) {
            // Change class
            $$('.view.navbar-through').removeClass('navbar-through').addClass('navbar-fixed');
            // And move Navbar into Page
            $$('#mainView .navbar').prependTo('#mainView .page');
        }
    },

    hideSplashScreen : function () {
        if (firstLoad == true) {
            window.loadService.loadComplete();
            window.firstLoad = false;
        }
    },

    createOutOfDatePopup : function (latestVersion) {
        setTimeout(function () {
            var url;

            if (isDesktop === true)
                url = "http://slychat.io";
            else if (isAndroid === true)
                url = "http://slychat.io";
            else if (isIos === true)
                url = "http://slychat.io";

            slychat.modal({
                title:  'Application out of date',
                text: 'Your application is out of date, please update for a better experience. Your version is ' + buildConfig.VERSION + ', the latest available version is ' + latestVersion + '.',
                buttons: [
                    {
                        text: 'Update',
                        onClick: function() {
                            platformService.openURL(url).catch(function (e) {
                                exceptionController.handleError(e);
                            });
                        }
                    },
                    {
                        text: 'Later',
                        onClick: function() {
                        }
                    }
                ]
            });
        }, 3000);
    },

    setAppTheme : function (theme) {
        if(!isIos) {
            var body = $("body");
            body.removeClass();

            switch (theme) {
                case null:
                    body.addClass(SettingsController.themesClassName[SettingsController.themesConfigName.dafaultTheme]);
                    break;

                case SettingsController.themesConfigName.darkTheme:
                    body.addClass(SettingsController.themesClassName[SettingsController.themesConfigName.darkTheme]);
                    break;

                case SettingsController.themesConfigName.whiteTheme:
                    if (!isIos)
                        body.addClass(SettingsController.themesClassName[SettingsController.themesConfigName.whiteTheme]);
                    break;
            }
        }
    }
};
