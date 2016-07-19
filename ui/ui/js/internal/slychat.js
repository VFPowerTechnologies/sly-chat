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

window.navigationController = new NavigationController();
window.loginController = new LoginController();
window.registrationController = new RegistrationController();
window.profileController = new ProfileController();
window.contactController = new ContactController();
window.chatController = new ChatController();
window.connectionController = new ConnectionController();

var firstLoad = true;

// Application initialization
var slychat = new Framework7({
    cache: false,
    swipeBackPage: false,
    tapHold: true,
    tapHoldDelay: 500,
    swipePanel: 'right',
    modalTitle: 'Sly'
});

var $$ = Dom7;
$.fn.intlTelInput.loadUtils("js/external-lib/utils.js");

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
