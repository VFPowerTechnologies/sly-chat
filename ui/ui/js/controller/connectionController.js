var ConnectionController = function () {
    this.networkAvailable = true;
    this.relayConnected = true;
};

ConnectionController.prototype = {
    init : function () {
        networkStatusService.addRelayStatusChangeListener(function (status) {
            console.log("relay status: " + status.online);
            console.log("this.relayConnected: " + this.relayConnected);
            this.relayConnected = status.online;
            this.handleConnectionDisplay();
        }.bind(this));

        networkStatusService.addNetworkStatusChangeListener(function (status) {
            console.log("network status: " + status.online);
            console.log("this.networkAvailable: " + this.networkAvailable);
            this.networkAvailable = status.online;
            this.handleConnectionDisplay();
        }.bind(this));
    },

    handleConnectionDisplay: function () {
        var networkStatus = $("#networkStatus");

        console.log("handle relay connected: " + this.relayConnected);
        console.log("handle network available: " + this.networkAvailable);

        if(this.networkAvailable == false) {
            console.log("in networkAvailable block");
            networkStatus.removeClass("hidden");
            networkStatus.find("span").html("No connection available");
        }
        else if(this.relayConnected == false) {
            console.log("in relayConnected block");
            networkStatus.removeClass("hidden");
            networkStatus.find("span").html("Disconnected");
        }
        else {
            console.log("in else block");
            networkStatus.addClass("hidden");
            networkStatus.find("span").html("");
        }
    }
};