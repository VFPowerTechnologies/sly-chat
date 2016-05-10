var NavigationController = function () {};

NavigationController.prototype = {
    init : function () {
        navigationService = {
            goBack: function () {
                this.goBack();
            }.bind(this),

            goTo: function(url) {
                this.goTo(url);
            }.bind(this)
        };
    },
    goTo : function (page) {
        if(page == "contacts") {
            historyService.clear().then(function () {
                KEYTAP.navigationController.loadPage("contacts.html", false);
            });
        }
        else if(/^user\//.test(page)) {
            var id = page.split("/", 2)[1];

            //so we clear the history and set it to contacts > chat
            historyService.replace(["contacts.html"]).then(function () {
                KEYTAP.contactController.loadContactPage(id, false);
            });
        }
        else {
            console.error("Unknown page: " + page);
        }
    },
    goBack : function () {
        var modal = $(".modal");
        if(modal.is(":visible")){
            modal.modal("hide");
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
        var currentUrl = window.location.href;
        var currentPage = currentUrl.substring(currentUrl.lastIndexOf("/") + 1);
        if(currentPage !== "index.html") {
            historyService.push(window.location.href).catch(function (e) {
                KEYTAP.exceptionController.displayDebugMessage(e);
                console.log(e);
            });
        }
    },
    loadPage : function (url, pushCurrentPage) {
        if(pushCurrentPage === undefined)
            pushCurrentPage = true;

        if(pushCurrentPage === true)
            this.pushHistory();

        this.smoothStateLoad(url);
    },
    clearHistory : function () {
        historyService.clear().catch(function (e){
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
            extra = KEYTAP.contactController.getCurrentContact().id;
        }

        if(page !== undefined) {
            eventService.dispatchEvent({
                "eventType": "PageChange",
                "page": page,
                "extra": extra
            });
        }

        this.load(url);
    },
    loadMessageLink : function (url) {
        platformService.openURL(url).catch(function (e) {
            KEYTAP.exceptionController.displayDebugMessage(e);
            console.log("An error occured while opening link " + e);
        });
    },
    load : function (url) {
        // SmoothState, makes only the main div reload on page load.
        $('#main').smoothState({
            onStart: {
                duration: 250,
                render: function ($container) {
                    if(window.location.href.indexOf("chat.html") > -1) {
                        $(window.location).trigger("chatExited", {});
                    }
                }
            },

            onReady: {
                duration: 0,
                render: function ($container, $newContent) {
                    $("#content").attr("id", "oldContent");
                    var newDiv = $("<div>");
                    newDiv.attr("id", "newMainDiv");
                    newDiv.css("width", "100%");
                    newDiv.css("position", "absolute");
                    newDiv.css("z-index", 3);
                    newDiv.addClass("m-scene toolbar-inside main-content");

                    var mainDiv = $("#main");

                    mainDiv.before(newDiv);
                    mainDiv.attr("id", "oldMain");

                    var fragment = $(document.createDocumentFragment());
                    fragment.append($newContent);

                    newDiv.html(fragment);

                    var height = window.innerHeight - 56;
                    newDiv.css("height", height + "px");

                    newDiv.attr("id", "main");

                    var oldMain = $("#oldMain");

                    oldMain.css("width", "100%");
                    oldMain.css("position", "absolute");
                    oldMain.css("z-index", 1);
                }
            },

            onAfter: function() {
                if(window.firstLoad === true) {
                    window.firstLoad = false;
                    loadService.loadComplete();
                }

                if($("#messages").length) {
                    KEYTAP.chatController.scrollTop();
                }

                setTimeout(function () {
                    var main = $("#main");
                    $("#oldMain").remove();
                    main.css('position', 'static');
                    main.css('z-index', 'auto');
                    KEYTAP.menuController.handleMenuDisplay();
                }, 450);
            }
        }).data('smoothState').load(url);
    }
};
