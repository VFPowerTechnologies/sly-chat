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

        if(this.networkAvailable == false) {
            networkStatus.removeClass("hidden");
            networkStatus.find("span").html("No connection available");
        }
        else if(this.relayConnected == false) {
            networkStatus.removeClass("hidden");
            networkStatus.find("span").html("Disconnected");
        }
        else {
            networkStatus.addClass("hidden");
            networkStatus.find("span").html("");
        }
    }
};