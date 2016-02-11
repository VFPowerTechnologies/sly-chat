window.registrationService = new RegistrationService();
window.platformInfoService = new PlatformInfoService();
window.messengerService = new MessengerService();
window.loginService = new LoginService();
window.contactService = new ContactsService();
window.historyService = new HistoryService();
window.develService = new DevelService();

// Create application namespace.
var KEYTAP = KEYTAP || {};

KEYTAP.contactModel = new ContactModel();
KEYTAP.contactController = new ContactController(KEYTAP.contactModel);

KEYTAP.chatModel = new ChatModel();
KEYTAP.chatController = new ChatController(KEYTAP.chatModel, KEYTAP.contactController);
KEYTAP.chatController.addMessageUpdateListener();
KEYTAP.chatController.addNewMessageListener();

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

// Back button listener
navigationService = {
    goBack: function () {
        goBack();
    }
};

// Go back function
function goBack(){
    historyService.pop().then(function(url){
        smoothState.load(url);
    }).catch(function (e){
        console.log(e);
    })
}

// Push the current location to the java side
function pushHistory(){
    historyService.push(window.location.href).then(function(){

    }).catch(function(e){
        console.log(e);
    });
}

// Loads a page using smoothState and push the current url to history
function loadPage(url){
    pushHistory();
    smoothState.load(url);
}

//Send a fake message to test receive message listener.
$(document).on("click", '#sendFakeMessage', function(e){
    e.preventDefault();

    develService.receiveFakeMessage(KEYTAP.contactController.getContact(0), "Fake").catch(function (e) {
        console.log('receiveFakeMessage failed: ' + e);
    });
});
