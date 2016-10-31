var NavigationController = function () {
    this.menu = [];
};

NavigationController.prototype = {
    init : function () {
        window.navigationService = {
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
                this.loadPage("contacts.html", false);
            }.bind(this));
        }
        else if(/^user\//.test(page)) {
            var id = page.split("/", 2)[1];

            //so we clear the history and set it to contacts > chat
            historyService.replace(["contacts.html"]).then(function () {
                if($$('.popup.modal-in').length > 0) {
                    $$('.popup.modal-in').find('.close-popup-btn').trigger('click');
                }
                else if ($$('.picker-modal.modal-in').length > 0) {
                    slychat.closeModal();
                }
                var contact = contactController.getContact(id);
                if (contact !== false)
                    contactController.loadChatPage(contact, false);
                else
                    contactController.fetchAndLoadChat(id);
            });
        }
        else if (/^group\//.test(page)) {
            var groupId = page.split("/", 2)[1];

            historyService.replace(['contacts.html']).then(function () {
                if($$('.popup.modal-in').length > 0) {
                    $$('.popup.modal-in').find('.close-popup-btn').trigger('click');
                }
                else if ($$('.picker-modal.modal-in').length > 0) {
                    slychat.closeModal();
                }
                var group = groupController.getGroup(groupId);
                if (group !== false)
                    contactController.loadChatPage(group, false, true);
                else
                    groupController.fetchAndLoadGroupChat(groupId);
            });
        }
        else {
            console.error("Unknown page: " + page);
        }

        uiController.hideSplashScreen();
    },

    goBack : function () {
        if($$('.popup.modal-in').length > 0) {
            $$('.popup.modal-in').find('.close-popup-btn').trigger('click');
        }
        else if ($$('.picker-modal.modal-in').length > 0 || $$(".actions-modal.modal-in").length > 0 || $$(".popover.modal-in").length > 0) {
            slychat.closeModal();
        }
        else if ($(".mobile-emoji-picker-opened").length > 0) {
            closeMobileEmoji();
        }
        else {
            historyService.pop().then(function (url) {
                if (url == "") {
                    windowService.minimize();
                }
                else {
                    this.load(url);
                }
            }.bind(this)).catch(function (e) {
                exceptionController.handleError(e);
            });
        }
    },

    loadPage : function (url, pushCurrentPage, options) {
        if (pushCurrentPage === undefined)
            pushCurrentPage = true;

        if (pushCurrentPage === true)
            this.pushHistory();

        this.load(url, options)
    },

    pushHistory : function () {
        var currentPage = this.getCurrentPage();

        if (currentPage !== "index.html") {
            historyService.push(currentPage).catch(function (e) {
                exceptionController.handleError(e);
            });
        }
    },

    clearHistory : function () {
        historyService.clear().catch(function (e){
            exceptionController.handleError(e);
        });
    },

    loadMessageLink : function (url) {
        platformService.openURL(url).catch(function (e) {
            exceptionController.handleError(e);
        });
    },

    load : function (url, options) {
        var currentContatctId = null;
        if(url === "chat.html") {
            if(options !== undefined && options.query !== undefined && options.query.id !== undefined)
                currentContatctId = options.query.id;
            else if (chatController.currentContact !== null) {
                // if loading chat from back button press.
                var currentContact = chatController.currentContact;
                currentContatctId = currentContact.id;
                options = {
                    url: "chat.html",
                    query: {
                        name: currentContact.name,
                        id: currentContatctId
                    },
                    group: currentContact.isGroup
                };

                if (!currentContact.isGroup) {
                    options.query.email = currentContact.email;
                    options.query.publicKey = currentContact.publicKey;
                    options.query.phoneNumber = currentContact.phoneNumber;
                }
            }
        }

        var currentState = {
            "currentPage" : url,
            "currentContact" : currentContatctId
        };

        stateService.setState(currentState).catch(function (e) {
            exceptionController.handleError(e);
        });

        var page;
        var extra = "";

        if(/contacts.html$/.test(url)) {
            page = "CONTACTS"
        }
        else if(/chat.html$/.test(url)) {
            if(options !== undefined && options.group !== undefined && options.group === true)
                page = "GROUP";
            else
                page = "CONVO";
            extra = currentContatctId;
        }

        if(page !== undefined) {
            this.dispatchEvent({
                "eventType": "PageChange",
                "page": page,
                "extra": extra
            });
        }

        if (options === undefined) {
            options = {
                url: url
            }
        }

        mainView.router.load(options);
    },

    dispatchEvent : function (event) {
        eventService.dispatchEvent(event);
    },

    getCurrentPage : function () {
        var page = $$('#mainView').data('page');

        return page + ".html";
    },

    createMenu : function () {
        this.menu = [
            {
                text: 'Profile',
                onClick: function () {
                    navigationController.loadPage('profile.html', true);
                }
            },
            {
                text: 'Settings',
                onClick: function () {
                    navigationController.loadPage('settings.html', true);
                }
            },
            {
                text: 'Blocked Contacts',
                onClick: function () {
                    navigationController.loadPage('blockedContacts.html', true);
                }
            },
            {
                text: 'Add Contact',
                onClick: function () {
                    navigationController.loadPage("addContact.html", true);
                }
            },
            {
                text: 'Create Group',
                onClick: function () {
                    navigationController.loadPage('createGroup.html', true);
                }
            }
        ];
        if (window.shareSupported) {
            this.menu.push({
                text: 'Invite a friend',
                onClick: function () {
                    navigationController.loadPage("inviteFriends.html", true);
                }
            })
        }

        this.menu.push(
            {
                text: 'Send Feedback',
                onClick: function () {
                    navigationController.loadPage("feedback.html", true);
                }
            },
            {
                text: 'Logout',
                onClick: function () {
                    loginController.logout();
                }
            },
            {
                text: 'Cancel',
                color: 'red',
                onClick: function () {
                }
            }
        );
    },

    openMenu : function () {
        slychat.actions(this.menu);
    },

    loadInitialPage : function () {
        var noStateLoad = [
            "register.html",
            "login.html",
            "smsVerification.html",
            "updatePhone.html",
            "registerStepOne.html",
            "registerStepTwo.html",
            "registerStepThree.html",
            "registerStepFour.html",
            "registerStepFive.html"
        ];

        stateService.getInitialPage().then(function (initialPage) {
            if(initialPage === null) {
                stateService.getState().then(function (state) {
                    if (state === null || state.currentPage === undefined || state.currentPage === null || state.currentPage == "login.html") {
                        navigationController.loadPage('contacts.html');
                        navigationController.clearHistory();
                    }
                    else {
                        if(state.currentPage.indexOf("chat.html") <= -1) {
                            if ($.inArray(state.currentPage, noStateLoad) > -1) {
                                navigationController.loadPage('contacts.html');
                                navigationController.clearHistory();
                            }
                            else {
                                navigationController.loadPage(state.currentPage, false);
                            }
                        }
                        else {
                            if (typeof state.currentContact != "undefined" && state.currentContact != null) {
                                contactController.fetchAndLoadChat(state.currentContact);
                            }
                            else {
                                navigationController.loadPage(state.currentPage, false);
                            }
                        }
                    }
                }).catch(function (e) {
                    exceptionController.handleError(e);
                });
            }
            else
            {
                navigationController.goTo(initialPage);
            }
        });
    },

    replaceHistory : function (list) {
        historyService.replace(list).catch(function (e) {
            exceptionController.handleError(e);
        })
    }
};
