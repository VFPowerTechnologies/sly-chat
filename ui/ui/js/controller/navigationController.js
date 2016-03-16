var NavigationController = function () {};

NavigationController.prototype = {
    init : function () {
        navigationService = {
            goBack: function () {
                this.goBack();
            }.bind(this)
        };
    },
    goBack : function () {
        historyService.pop().then(function(url){
            if(url == "") {
                windowService.minimize();
            }
            else {
                this.smoothStateLoad(url);
            }
        }.bind(this)).catch(function (e){
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log(e);
        })
    },
    pushHistory : function () {
        historyService.push(window.location.href).then(function(){

        }).catch(function(e){
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log(e);
        });
    },
    loadPage : function (url) {
        this.pushHistory();
        this.smoothStateLoad(url);
    },
    clearHistory : function () {
        historyService.clear().then(function() {
        }).catch(function (e){
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log("Could not clear history : " + e);
        });
    },
    smoothStateLoad : function (url) {
        var currentState = {
            "currentPage" : url,
            "currentContact" : KEYTAP.contactController.getCurrentContact()
        };
        stateService.setState(currentState).catch(function (e) {
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log(e);
        });
        smoothState.load(url);
    }
};