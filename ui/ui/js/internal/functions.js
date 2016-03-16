if (typeof KEYTAP == "undefined") {
    // Create application namespace.
    var KEYTAP = KEYTAP || {};

    window.registrationService = new RegistrationService();
    window.platformInfoService = new PlatformInfoService();
    window.messengerService = new MessengerService();
    window.loginService = new LoginService();
    window.contactService = new ContactsService();
    window.historyService = new HistoryService();
    window.develService = new DevelService();
    window.networkStatusService = new NetworkStatusService();
    window.configService = new ConfigService();
    window.stateService = new StateService();
    window.telephonyService = new TelephonyService();
    window.windowService = new WindowService();

    networkStatusService.addRelayStatusChangeListener(function (status) {
        var networkStatus = $("#networkStatus");
        if(status.online == false){
            networkStatus.removeClass("hidden");
            networkStatus.find("span").html("Disconnected");
        }else{
            networkStatus.addClass("hidden");
        }
    });

    networkStatusService.addNetworkStatusChangeListener(function (status) {
        var networkStatus = $("#networkStatus");
        if(status.online == false){
            networkStatus.removeClass("hidden");
            networkStatus.find("span").html("No connection Available");
        }else{
            networkStatus.addClass("hidden");
        }
    });

    KEYTAP.exceptionController = new ExceptionController();

    KEYTAP.loginController = new LoginController(new LoginModel);
    KEYTAP.loginController.init();

    KEYTAP.registrationController = new RegistrationController(new RegistrationModel());
    KEYTAP.registrationController.init();
    registrationService.addListener(function (registrationStatus) {
        $("#registrationStatusUpdate").html(registrationStatus + "...");
    });

    KEYTAP.contactController = new ContactController(new ContactModel());

    KEYTAP.chatController = new ChatController(new ChatModel(), KEYTAP.contactController);
    KEYTAP.chatController.addMessageUpdateListener();
    KEYTAP.chatController.addNewMessageListener();

    KEYTAP.navigationController = new NavigationController();
    KEYTAP.navigationController.init();

    KEYTAP.menuController = new MenuController();
    KEYTAP.menuController.init();

    $(window.location).on("chatExited", function () {
        KEYTAP.chatController.model.markConversationAsRead(KEYTAP.contactController.getCurrentContact());
    });

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
                    if(window.location.href.indexOf("chat.html") > -1) {
                        $(window.location).trigger("chatExited", {});
                    }
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

//mouseheld event to trigger contact menu
    (function($) {
        function startTrigger(e) {
            var $elem = $(this);
            $elem.data('mouseheld_timeout', setTimeout(function () {
                $elem.trigger('mouseheld');
            }, e.data));
        }

        function stopTrigger() {
            var $elem = $(this);
            clearTimeout($elem.data('mouseheld_timeout'));
        }


        var mouseheld = $.event.special.mouseheld = {
            setup: function (data) {
                var $this = $(this);
                $this.bind('mousedown', +data || mouseheld.time, startTrigger);
                $this.bind('mouseleave mouseup', stopTrigger);
            },
            teardown: function () {
                var $this = $(this);
                $this.unbind('mousedown', startTrigger);
                $this.unbind('mouseleave mouseup', stopTrigger);
            },
            time: 1000
        };
    })(jQuery);

    function validateEmail(email) {
        var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
        return re.test(email);
    }
}