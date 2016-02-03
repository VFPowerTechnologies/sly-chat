window.registrationService = new RegistrationService();
window.platformInfoService = new PlatformInfoService();
window.messengerService = new MessengerService();
window.loginService = new LoginService();
window.contactService = new ContactsService();
window.historyService = new HistoryService();

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

window.navigationService = {
    goBack: function () {
        goBack();
    }
};

function goBack(){
//    console.log('Back button pressed');

    historyService.pop().then(function(url){
        smoothState.load(url);
    }).catch(function (e){
        console.log(e);
    })
}

function pushHistory(){
    historyService.push(window.location.href).then(function(){
//        console.log("history pushed");
    }).catch(function(e){
        console.log(e);
    });
}

function loadPage(url){
    pushHistory();

    smoothState.load(url);
}





      