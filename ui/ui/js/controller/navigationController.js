var NavigationController = function () {

}

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
            smoothState.load(url);
        }).catch(function (e){
            console.log(e);
        })
    },
    pushHistory : function () {
        historyService.push(window.location.href).then(function(){

        }).catch(function(e){
            console.log(e);
        });
    },
    loadPage : function (url) {
        this.pushHistory();
        smoothState.load(url);
    },
    clearHistory : function () {
        historyService.clear().then(function() {
        }).catch(function (e){
            console.log("Could not clear history : " + e);
        });
    }
}