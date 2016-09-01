var UIController = function () {
    window.registrationService = new RegistrationService();
    window.platformInfoService = new PlatformInfoService();
    window.messengerService = new MessengerService();
    window.loginService = new LoginService();
    window.contactService = new ContactsService();
    window.historyService = new HistoryService();
    window.develService = new DevelService();
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

    window.relayTimeDifference = 0;

    window.$$ = Dom7;
    $.fn.intlTelInput.loadUtils("js/external-lib/utils.js");
};

UIController.prototype = {
    startUI : function () {
        this.initApplication();
        this.initMainView();
        this.handlePlatformUpdate();
        this.initController();
        this.addTimeDifferenceListener();
    },

    addTimeDifferenceListener : function () {
        messengerService.addClockDifferenceUpdateListener(function (difference) {
            window.relayTimeDifference = difference;
        }).catch(function (e) {
            exceptionController.handleError(e);
        })
    },

    initController : function () {
        navigationController.init();
        loginController.init();
        chatController.init();
        contactController.addContactEventListener();
        connectionController.init();
        groupController.addGroupEventListener();
    },

    initApplication : function () {
        window.isAndroid = Framework7.prototype.device.ios === false;
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
    }
};
