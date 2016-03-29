var NavigationController = function () {};

NavigationController.prototype = {
    init : function () {
        navigationService = {
            goBack: function () {
                this.goBack();
            }.bind(this),

            goTo: function(url) {
                if(url == "contacts") {
                    historyService.clear().then(function () {
                        KEYTAP.navigationController.loadPage("contacts.html", false);
                    });
                }
                else if(url.startsWith("user/")) {
                    var email = url.split("/", 2)[1];

                    //so we clear the history and set it to contacts > chat
                    historyService.replace(["contacts.html"]).then(function () {
                        KEYTAP.contactController.loadContactPage(email, false);
                    });
                }
            }.bind(this)
        };
    },
    goBack : function () {
        if($(".modal").is(":visible")){
            $(".modal").modal("hide");
        }
        else{
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
        }
    },
    pushHistory : function () {
        historyService.push(window.location.href).then(function(){

        }).catch(function(e){
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log(e);
        });
    },
    loadPage : function (url, pushCurrentPage) {
        if(pushCurrentPage === undefined)
            pushCurrentPage = true;

        if(pushCurrentPage === true)
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

        var page;
        var extra = "";

        //stuff pushed onto history ends up with full file:// urls, so we need
        //to test via re
        if(/contacts.html$/.test(url)) {
            page = "CONTACTS"
        }
        else if(/chat.html$/.test(url)) {
            page = "CONVO";
            extra = KEYTAP.contactController.getCurrentContact().email;
        }

        if(page !== undefined) {
            eventService.dispatchEvent({
                "eventType": "PageChange",
                "page": page,
                "extra": extra
            });
        }

        smoothState.load(url);
    }
};
