var ConnectionController = function () {
    this.networkAvailable = true;
    this.relayConnected = true;
    this.connectionNotification = null;
};

ConnectionController.prototype = {
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
    handleConnectionDisplay: function () {
        var networkStatus = $("#networkStatus");
        var connectionStatus = $("#connectionStatus");

        var currentUrl = window.location.href;
        var page = currentUrl.substring(currentUrl.lastIndexOf("/") + 1);
        var notShowPageList = ["register.html", "login.html", "updatePhone.html", "smsVerification.html", "index.html"];


        if(notShowPageList.indexOf(page) == -1) {
            if (this.networkAvailable == false) {
                this.updateNotification("No network available", "danger");
                $("#addContactBtn").prop("disabled", true);
            }
            else if (this.relayConnected == false) {
                this.updateNotification("Disconnected", "warning");
                $("#addContactBtn").prop("disabled", false);
            }
            else {
                this.closeNotification();
                $("#addContactBtn").prop("disabled", false);
            }
        }
    },
    openNotification : function (message, notificationClass) {
        this.connectionNotification = $.notify({
            icon: "icon-pull-left fa fa-info-circle",
            message: message
        }, {
            type: notificationClass,
            newest_on_top: true,
            delay: 0,
            allow_dismiss: false,
            offset: {
                y: 66,
                x: 20
            }
        });
    },
    closeNotification : function () {
        if(this.connectionNotification != null) {
            this.connectionNotification.close();
            this.connectionNotification = null;
        }
    },
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
