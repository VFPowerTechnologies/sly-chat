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
    window.eventService = new EventService();
    window.accountModifictationService = new AccountModificationService();
    window.platformService = new PlatformService();

    KEYTAP.exceptionController = new ExceptionController();
    KEYTAP.loginController = new LoginController(new LoginModel);
    KEYTAP.registrationController = new RegistrationController(new RegistrationModel());
    KEYTAP.contactController = new ContactController(new ContactModel());
    KEYTAP.recentChatController = new RecentChatController(new RecentChatModel());
    KEYTAP.chatController = new ChatController(new ChatModel(), KEYTAP.contactController);
    KEYTAP.navigationController = new NavigationController();
    KEYTAP.menuController = new MenuController();
    KEYTAP.connectionController = new ConnectionController();
    KEYTAP.userInfoController = new UserInfoController(new UserInfoModel());

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

    function parseFormatedTimeString (timestamp) {
        return new Date(timestamp);
    }

    function createAvatar (name, color) {
        if(color === undefined)
            color = "#212121";
        var img = new Image();
        img.setAttribute('data-name', name);
        img.setAttribute('class', 'avatarCircle');

        $(img).initial({
            textColor: '#fff',
            seed: 0,
            backgroundColor: color
        });

        return img.outerHTML;
    }

    function createStatusModal(htmlContent) {
        var content = $(document.createElement("div"));
        content.addClass("container");

        var modalContent = $(document.createElement("div"));
        modalContent.addClass("valign-wrapper");
        modalContent.addClass("row");

        var container = $(document.createElement("div"));
        container.addClass("valign");
        container.html(htmlContent);

        content.append(container);

        modalContent.append(content);

        var html = $("<div>").append(modalContent).html();

        var bd = new BootstrapDialog();
        bd.setCssClass("statusModal");
        bd.setClosable(false);
        bd.setMessage(html);

        return bd;
    }

    function resizeWindow() {
        var height = window.innerHeight - 56;
        $("#main").css("height", height + "px");

        if ($("#currentPageChatEmail").length && $("#messages").length) {
            KEYTAP.chatController.scrollTop();
        }
    }
}

$(document).ready(function () {
    KEYTAP.loginController.init();

    KEYTAP.registrationController.init();
    registrationService.addListener(function (registrationStatus) {
        $("#registrationStatusUpdate").html(registrationStatus + "...");
    });

    KEYTAP.contactController.addContactListSyncListener();

    KEYTAP.chatController.addMessageUpdateListener();
    KEYTAP.chatController.addNewMessageListener();
    KEYTAP.chatController.createChatLinkEvent();

    KEYTAP.navigationController.init();

    KEYTAP.menuController.init();

    $(window.location).on("chatExited", function () {
        KEYTAP.chatController.model.markConversationAsRead(KEYTAP.contactController.getCurrentContact());
    });

    KEYTAP.connectionController.init();
});
