var ConnectionController = function () {
    this.networkAvailable = true;
    this.relayConnected = true;
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

        if(this.networkAvailable == false) {
            networkStatus.removeClass("hidden");
            connectionStatus.addClass("hidden");
            $("#addContactBtn").prop("disabled", true);
        }
        else if(this.relayConnected == false) {
            networkStatus.addClass("hidden");
            connectionStatus.removeClass("hidden");
            $("#addContactBtn").prop("disabled", false);
        }
        else {
            networkStatus.addClass("hidden");
            connectionStatus.addClass("hidden");
            $("#addContactBtn").prop("disabled", false);
        }
    }
};