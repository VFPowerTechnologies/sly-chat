var UIController = function () {
    window.registrationService = new RegistrationService();
    window.platformInfoService = new PlatformInfoService();
    window.messengerService = new MessengerService();
    window.loginService = new LoginService();
    window.resetAccountService = new ResetAccountService();
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
    window.accountResetController = new ResetAccountController();
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
    init : function () {
        platformInfoService.getInfo().then(function (info) {
            this.startUI(info)
        }.bind(this)).catch(function (e) {
            exceptionController.handleError(e)
        })
    },

    startUI : function (info) {
        this.initApplication(info);
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

    initApplication : function (info) {
        window.isAndroid = info.name === "android";
        window.isIos = info.name === "ios";
        window.isDesktop = info.name === "desktop";
        window.isOsx = info.os === "osx";
        window.isWindows = info.os === "windows";
        window.isLinux = info.os === "linux";

        Template7.global = {
            android: isAndroid,
            ios: isIos,
            isDesktop: isDesktop,
            isOsx: isOsx,
            isLinux: isLinux,
            isWindows: isWindows
        };

        window.firstLoad = true;
        window.firstLogin = true;

        var options = {
            desktop: isDesktop,
            material: !isIos,
            cache: false,
            swipeBackPage: false,
            tapHold: true,
            tapHoldDelay: 500,
            modalTitle: 'Sly',
            template7Pages: true
        };

        if (isIos) {
            this.createIosMenu();
            options.swipePanel = 'right';
            options.swipePanelOnlyClose = true;
        }

        window.slychat = new Framework7(options);

        addPagesListeners();
        createFormValidation();
    },

    createIosMenu : function () {
        var menu = $('<div class="panel-overlay"></div>' +
            '<div id="iosMenu" class="panel panel-right panel-cover">' +
            '<div class="ios-menu-header" style="min-height: 100px; text-align: center; padding-bottom: 5px; border-bottom: 1px solid #eee;">' +
            '<div style="height: 80px;">' +
            '<img style="height: 80px; width: 80px; display: block; margin: auto;" src="img/sly_logo.png"/>' +
            '</div>' +
            '<p id="rightDrawerUserName" style="color: #fff; margin: 0 10px;"></p>' +
            '<p id="rightDrawerUserEmail" style="color: #fff; margin: 0 10px;"></p>' +
            '</div>' +
            '<div class="list-block">' +
            '<ul id="iosMenuList">' +
            '<li id="menuProfileLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-user"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Profile</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuSettingsLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-cogs"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Settings</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuAddContactLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-user-plus"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Add Contact</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuCreateGroupLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-users"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Create Group</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuBlockedContactsLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-ban"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Blocked Contacts</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuFeedbackLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-commenting"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Feedback</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuLogoutLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-sign-out"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Logout</div>' +
            '</div>' +
            '</li>' +
            '</ul>' +
            '</div>' +
            '</div>');

        menu.find("#menuProfileLink").click(function () {
            navigationController.loadPage('profile.html', true);
        });

        menu.find("#menuSettingsLink").click(function () {
            navigationController.loadPage('settings.html', true);
        });

        menu.find("#menuBlockedContactsLink").click(function () {
            navigationController.loadPage('blockedContacts.html', true);
        });

        menu.find("#menuAddContactLink").click(function () {
            navigationController.loadPage("addContact.html", true);
        });

        menu.find("#menuCreateGroupLink").click(function () {
            navigationController.loadPage('createGroup.html', true);
        });

        menu.find("#menuFeedbackLink").click(function () {
            navigationController.loadPage("feedback.html", true);
        });

        menu.find("#menuLogoutLink").click(function () {
            loginController.logout();
        });

        $('body').prepend(menu);
    },

    addInviteToIosMenu : function () {
        var iosMenu = $("#iosMenu");
        if (iosMenu.length > 0) {
            var invite = $('<li id="menuInviteFriendsLink" class="item-content close-panel">' +
                '<div class="item-media"><i class="fa fa-share-alt"></i></div>' +
                    '<div class="item-inner">' +
                        '<div class="item-title">Invite Friends</div>' +
                    '</div>' +
                '</li>');

            invite.click(function () {
                navigationController.loadPage("inviteFriends.html", true);
            });

            iosMenu.find("#menuBlockedContactsLink").after(invite);
        }
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
            if (isSupported)
                uiController.addInviteToIosMenu();

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
