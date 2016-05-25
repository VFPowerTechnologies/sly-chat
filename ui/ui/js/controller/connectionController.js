var ConnectionController = function () {
    this.networkAvailable = true;
    this.relayConnected = true;
    this.connectionNotification = null;
};

ConnectionController.prototype = {
    /**
     * Init function, create the listener for relay connection and network connection.
     * Ran only once on connection controller initiation.
     */
    init : function () {
        networkStatusService.addRelayStatusChangeListener(function (status) {
            this.relayConnected = status.online;
            this.handleConnectionDisplay();
        }.bind(this));

        networkStatusService.addNetworkStatusChangeListener(function (status) {
            this.networkAvailable = status.online;
            this.handleConnectionDisplay();
        }.bind(this));
    },
    /**
     * Handle connection notification display.
     */
    handleConnectionDisplay: function () {
        var networkStatus = $("#networkStatus");
        var connectionStatus = $("#connectionStatus");

        if (this.networkAvailable == false) {
            this.updateNotification("No network available", "danger");
            $("#addContactBtn").prop("disabled", true);
        }
        else if (this.relayConnected == false) {
            setTimeout(function () {
                if(this.relayConnected == false && this.networkAvailable == true) {
                    this.updateNotification("Waiting for connection...", "warning");
                    $("#addContactBtn").prop("disabled", false);
                }
            }.bind(this), 2000);
        }
        else {
            this.closeNotification();
            $("#addContactBtn").prop("disabled", false);
        }
    },
    /**
     * Opens or update the notification if it's already opened.
     *
     * @param message
     * @param notificationClass
     */
    openNotification : function (message, notificationClass) {
        var currentUrl = window.location.href;
        var page = currentUrl.substring(currentUrl.lastIndexOf("/") + 1);
        var notShowPageList = ["register.html", "login.html", "updatePhone.html", "smsVerification.html", "index.html"];

        if(notShowPageList.indexOf(page) == -1) {
            this.connectionNotification = $.notify({
                icon: "icon-pull-left fa fa-info-circle",
                message: message
            }, {
                type: notificationClass,
                newest_on_top: true,
                delay: 0,
                allow_dismiss: false,
                allow_duplicates: false,
                offset: {
                    y: 66,
                    x: 20
                }
            });
        }
    },
    /**
     * Close the notification.
     */
    closeNotification : function () {
        if(this.connectionNotification != null) {
            this.connectionNotification.close();
            this.connectionNotification = null;
        }
    },
    /**
     * Update the notification.
     *
     * @param message
     * @param notificationClass
     */
    updateNotification : function (message, notificationClass) {
        if(this.connectionNotification != null) {
            this.connectionNotification.update("message", message);
            this.connectionNotification.update("type", notificationClass);
        }
        else {
            this.openNotification(message, notificationClass);
        }
    }
};
