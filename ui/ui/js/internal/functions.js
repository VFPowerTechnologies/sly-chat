window.registrationService = new RegistrationService();
window.platformInfoService = new PlatformInfoService();
window.messengerService = new MessengerService();
window.loginService = new LoginService();
window.contactService = new ContactsService();
window.historyService = new HistoryService();
window.develService = new DevelService();

// Create application namespace.
var KEYTAP = KEYTAP || {};

KEYTAP.exceptionController = new ExceptionController();

KEYTAP.loginController = new LoginController(new LoginModel);
KEYTAP.registrationController = new RegistrationController(new RegistrationModel());

KEYTAP.contactController = new ContactController(new ContactModel());

KEYTAP.chatController = new ChatController(new ChatModel(), KEYTAP.contactController);
KEYTAP.chatController.addMessageUpdateListener();
KEYTAP.chatController.addNewMessageListener();

KEYTAP.navigationController = new NavigationController();
KEYTAP.navigationController.init();

KEYTAP.menuController = new MenuController();
KEYTAP.menuController.init();

// SmoothState, makes only the main div reload on page load.
$(function(){
    'use strict';
    var duration_CONSTANT = 250;
    var options = {
        prefetch: true,
        cacheLength: 20,
        onStart: {
            duration: duration_CONSTANT,
            render: function ($container) {
                $container.addClass('is-exiting');
                smoothState.restartCSSAnimations();
            }
        },

        onReady: {
            duration: 0,
            render: function ($container, $newContent) {
                $container.removeClass('is-exiting');
                $container.html($newContent);
            }
        }
    };

    window.smoothState = $('#main').smoothState(options).data('smoothState');
});
