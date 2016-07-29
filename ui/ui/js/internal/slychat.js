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

window.navigationController = new NavigationController();
window.loginController = new LoginController();
window.registrationController = new RegistrationController();
window.profileController = new ProfileController();
window.contactController = new ContactController();
window.chatController = new ChatController();
window.connectionController = new ConnectionController();
window.groupController = new GroupController();
window.exceptionController = new ExceptionController();

var isAndroid = Framework7.prototype.device.ios === false;
var isIos = Framework7.prototype.device.ios === true;

Template7.global = {
    android: isAndroid,
    ios: isIos
};

var firstLoad = true;

// Application initialization
var slychat = new Framework7({
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

if(!isIos)
    $("body").addClass("theme-deeporange");
else
    $("body").addClass("theme-orange");

var $$ = Dom7;
$.fn.intlTelInput.loadUtils("js/external-lib/utils.js");

if (!isIos) {
    // Change class
    $$('.view.navbar-through').removeClass('navbar-through').addClass('navbar-fixed');
    // And move Navbar into Page
    $$('#mainView .navbar').prependTo('#mainView .page');
}

// Add view
var mainView = slychat.addView('.view-main', {
    dynamicNavbar: true
});

// Controller init
navigationController.init();
loginController.init();
chatController.init();
contactController.addContactEventListener();
connectionController.init();
groupController.addGroupEventListener();
