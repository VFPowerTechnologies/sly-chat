// Code to run on each page load.
$$(document).on('pageBeforeInit', function (e) {
    var page = e.detail.page;
    if (isDesktop) {
        var mainView = $(".view-main");
        var leftMenu = $("#leftMenuPanel");

        switch (page.name) {
            case "contacts":
            case "chat":
            case "addContact":
            case "createGroup":
            case "profile":
                mainView.removeClass("left-menu-hidden");
                leftMenu.removeClass("hidden");
                break;

            case "login":
            case "register":
            case "smsVerification":
            case "updatePhone":
                mainView.addClass("left-menu-hidden");
                leftMenu.addClass("hidden");
                break;
        }
    }
});

slychat.onPageInit('registerStepOne', function () {
    uiController.hideSplashScreen();
    var nameInput = $("#name");
    nameInput.focus();
    $("#stepOneForm").submit(function (e) {
        e.preventDefault();
        registrationController.handleFirstStep();
    });

    if (registrationController.name !== "")
        nameInput.val(registrationController.name);

    $("#loginGoBtn").click(function () {
        navigationController.loadPage("login.html", true);
    });
});

slychat.onPageInit('registerStepTwo', function () {
    var emailInput = $("#email");
    emailInput.focus();
    $("#stepTwoForm").submit(function (e) {
        e.preventDefault();
        registrationController.handleSecondStep();
    });

    if (registrationController.email !== "")
        emailInput.val(registrationController.email);
});

slychat.onPageInit('registerStepThree', function () {
    $("#password").focus();

    $("#stepThreeForm").submit(function (e) {
        e.preventDefault();
        registrationController.handleThirdStep();
    });

    var options = {};
    options.ui = {
        container: "#pwd-container",
        showVerdictsInsideProgressBar: true,
        viewports: {
            progress: ".pwstrength_viewport_progress"
        }
    };
    $('#password').pwstrength(options);
});

slychat.onPageInit('registerStepFour', function () {
    $("#stepFourForm").submit(function (e) {
        e.preventDefault();
        registrationController.handleFourthStep();
    });

    updatePhoneWithIntl();

    $$('#countrySelect').on("change", function() {
        var ext = $("#countrySelect :selected").text().split("+")[1];
        setPhoneExt(ext);
        // TODO Validate Phone Input
    });
});

slychat.onPageInit('registerStepFive', function (page) {
    var email = page.query.email !== undefined ? page.query.email : registrationController.registrationInfo.email;
    var password = page.query.password !== undefined ? page.query.password : registrationController.registrationInfo.password;

    $("#smsVerificationCode").focus();
    $("#stepFiveForm").submit(function (e) {
        e.preventDefault();
        registrationController.handleFinalStep(email, password);
    });

    $$('#resendVerificationCode').on('click', function () {
        $$('#resendVerificationCode').prop('disabled', true);
        registrationController.resendVerificationCode();
    });

    $$('#updatePhoneNumberLink').on('click', function () {
        var options = {
            url: 'updatePhone.html',
            query: {
                email: email,
                password: password
            }
        };

        navigationController.loadPage("updatePhone.html", true, options);
    });
});

slychat.onPageInit('login', function (page) {
    uiController.hideSplashScreen();

    configService.getLoginRememberMe().then(function (v) {
        $$("#rememberMe").prop("checked", v);
    }).catch(function (e) {
        console.error("Unable to fetch app config: " + e.message);
    });

    $$("#rememberMe").on("change", function (e) {
        configService.setLoginRememberMe($$(this).prop("checked"));
    });

    $$('#submitLogin').on('click', function (e) {
        e.preventDefault();
        loginController.login();
    });

    $$('#registrationGoBtn').on('click', function () {
        if (isDesktop)
            navigationController.loadPage('register.html', true);
        else
            navigationController.loadPage("registerStepOne.html", true);
    });

    if (isDesktop) {
        $("#login").focus();
    }
});

slychat.onPageInit('register', function (page) {
    uiController.hideSplashScreen();
    updatePhoneWithIntl();
    $$('#countrySelect').on("change", function(e) {
        var ext = $("#countrySelect :selected").text().split("+")[1];
        setPhoneExt(ext);
        // TODO Validate Phone Input
    });

    $$('#registration-submit-button').on('click', function (e) {
        e.preventDefault();
        registrationController.register();
    });

    $$("#loginGoBtn").on('click', function (e) {
        navigationController.loadPage('login.html', true);
    });

    var options = {};
    options.ui = {
        container: "#pwd-container",
        showVerdictsInsideProgressBar: true,
        viewports: {
            progress: ".pwstrength_viewport_progress"
        }
    };
    $('#registration-password').pwstrength(options);
});

slychat.onPageInit('chat', function (page) {
    if(!isIos)
        emojiController.createPicker();

    var isGroup = page.query.email === undefined;
    var newMessageInput = $("#newMessageInput");
    contactController.resetUnreadCount(page.query.id, isGroup);

    if (!isGroup) {
        chatController.fetchMessageFor(0, 100, page.query);
    }
    else {
        groupController.fetchGroupMessage(0, 100, page.query.id);
    }

    configService.getLastMessageTtl().then(function (v) {
        chatController.lastMessageTtl = v;
    }).catch(function (e) {
        exceptionController.handleError(e);
    });

    $(".chat-page-contact-menu").click(function (e) {
        e.preventDefault();
        if (isGroup)
            chatController.openGroupPageMenu(page.query.id);
        else
            chatController.openContactPageMenu(page.query.id);
    });

    $$('#contact-name').html(page.query.name);
    $$('#contact-id').html(page.query.id);

    newMessageInput.on('keypress', function(e) {
        if (isDesktop) {
            if (e.keyCode === 13 && !e.ctrlKey && !e.shiftKey) {
                e.preventDefault();
                chatController.handleSubmitMessage(page.query);
            }
        }

        if ($(this).val() == "")
            $(this).addClass("empty-textarea");
        else
            $(this).removeClass("empty-textarea");
    });

    if (isDesktop) {
        newMessageInput.focus();
        $("#submitNewChatMessage").click(function (e) {
            e.preventDefault();
            e.stopPropagation();
            newMessageInput.focus();
            chatController.handleSubmitMessage(page.query);
        });

        $("#inputChatExpireMessageBtn").click(function (e) {
            e.preventDefault();
            e.stopPropagation();

            chatController.toggleExpiringMessageDisplay();
        });

        $("#emojiPickerBtn").on('click', function (e) {
            e.preventDefault();
            e.stopPropagation();

            slychat.popover(".popover-emoji", this);
        });
    }
    else {
        $("#submitNewChatMessage").on('touchstart', function (e) {
            e.preventDefault();
            e.stopPropagation();
            chatController.handleSubmitMessage(page.query);
        });

        $("#inputChatExpireMessageBtn").on('touchstart', function (e) {
            e.preventDefault();
            e.stopPropagation();

            chatController.toggleExpiringMessageDisplay();
        });

        if(!isIos) {
            $("#emojiPickerBtn").on('touchstart', function (e) {
                e.preventDefault();
                e.stopPropagation();
                emojiController.toggleMobileEmoji();
            });

            newMessageInput.emojioneArea({
                autoHideFilters: true,
                useInternalCDN: false,
                autocomplete: false,
                recentEmojis: false,
                attributes: {
                    autocomplete: 'on'
                },
                events: {
                    click: function (editor, event) {
                        editor.focus();
                    }
                }
            });

            $(".emojionearea-editor").on("touchstart", function (e) {
                e.stopImmediatePropagation();
            });
        }
    }
});

slychat.onPageInit('addContact', function (page) {
    $$('#newContactSearchSubmit').on('click', function (e) {
        e.preventDefault();
        contactController.newContactSearch();
    });
});

slychat.onPageBeforeInit('contacts', function (page) {
    if(isIos)
        createIosMenu();
    contactController.init();
    groupController.init();
});

slychat.onPageInit('smsVerification', function (page) {
    $$('#hiddenEmail').val(page.query.email);
    $$('#hiddenPassword').val(page.query.password);

    if (isDesktop)
        $("#smsCode").focus();

    $$('#submitVerificationCode').on('click', function (e) {
        e.preventDefault();
        registrationController.submitVerificationCode();
    });

    $$('#resendVerificationCode').on('click', function () {
        $$('#resendVerificationCode').prop('disabled', true);
        registrationController.resendVerificationCode();
    });

    $$('#updatePhoneNumberLink').on('click', function () {
        var options = {
            url: 'updatePhone.html',
            query: {
                email: page.query.email,
                password: page.query.password
            }
        };

        navigationController.loadPage("updatePhone.html", true, options);
    });
});

slychat.onPageInit('updatePhone', function (page) {
    updatePhoneWithIntl();
    $$('#hiddenEmail').val(page.query.email);

    $$('#countrySelect').on("change", function(e) {
        var ext = $("#countrySelect :selected").text().split("+")[1];
        setPhoneExt(ext);
        //TODO Validate Phone Input
    });

    $$('#submitPhoneUpdateBtn').on('click', function (e) {
        e.preventDefault();
        registrationController.updatePhone();
    });

    $$("#backToSmsVerificationLink").on('click', function () {
        var url;
        if (isDesktop)
            url = 'smsVerification.html';
        else
            url = 'registerStepFive.html';

        var options = {
            url: url,
            query: {
                email: page.query.email,
                password: page.query.password
            }
        };

        navigationController.loadPage(url, true, options);
    })
});

slychat.onPageInit('profile', function (page) {
    profileController.displayInfo();

    $$('#openProfileUpdatePopup').on('click', function () {
        profileController.openProfileEditPopup();
    });
});

slychat.onPageInit('createGroup', function (page) {
    groupController.insertContactList();

    $("#createNewGroupBtn").click(function (e) {
        e.preventDefault();
        groupController.createGroup();
    })
});

slychat.onPageInit('contactInfo', function (page) {
    var contact = false;
    if (page.query.contactId === undefined) {
        contact = contactController.getContact(contactController.lastContactInfoId);
    }
    else {
        contact = contactController.getContact(page.query.contactId);
    }

    if (contact !== false) {
        $("#contactName").html(contact.name);
        $("#contactEmail").html(contact.email);
        $("#contactPubKey").html(formatPublicKey(contact.publicKey));
    }
});

slychat.onPageInit('groupInfo', function (page) {
    var group;
    if (page.query.groupId === undefined) {
        group = groupController.getGroup(groupController.lastGroupId);
    }
    else {
        group = groupController.getGroup(page.query.groupId);
    }

    var members = false;
    if (group !== false) {
        $("#groupIdHidden").html(group.id);
        $("#groupName").html(group.name);

        members = groupController.getGroupMembers(group.id);

        if (members !== false) {
            groupController.createGroupInfoMemberList(members);
        }
    }

});

slychat.onPageInit('settings', function (page) {
    settingsController.onPageInit();
});

slychat.onPageInit('blockedContacts', function () {
    contactController.blockedContactPageInit();
});

slychat.onPageInit('feedback', function () {
    feedbackController.pageInit();
});

slychat.onPageInit("inviteFriends", function () {
    $("#submitInviteFriends").click(function (e) {
        e.preventDefault();
        $("#inviteFriendsError").html();
        var text = $("#inviteFriendsText").val();

        if (text.length <= 0)
            $("#inviteFriendsError").html("Invite message is needed");
        else {
            shareService.inviteToSly(
                'Join Sly Now!',
                text,
                'Get <a href="https://slychat.io">Sly</a>'
            ).catch(exceptionController.handleError);
        }
    })
});

$("#contactPopupNewBtn").on("click", function (e) {
    e.preventDefault();
    if ($("#contact-tab").hasClass("active")) {
        navigationController.loadPage('addContact.html', true);
    }
    else {
        navigationController.loadPage('createGroup.html', true);
    }
});

$(window).resize(function () {
    if($$('#mainView').data('page') == "chat")
        chatController.scrollTop();
});

$$(document).on("input", ".invalid-required", function(e) {
    if ($(this).val().trim() !== "") {
        $(this).removeClass("invalid-required");
    }
    else {
        $(this).addClass("invalid-required");
    }
});

$$(document).on('click', '.backBtn', function () {
    navigationController.goBack();
});

$$(document).on('click', '#logoutBtn', function (e) {
    e.preventDefault();
    loginController.logout();
});

$$(document).on('click', '.chatLink', function (e) {
    e.preventDefault();
    e.stopPropagation();
    navigationController.loadMessageLink($$(this).data('href'));
});

$$(document).on('click', '.open-action-menu', function (e) {
    e.preventDefault();
    navigationController.openMenu();
});

$$(document).on('click', '.create-group-button', function (e) {
    e.preventDefault();
    navigationController.loadPage('createGroup.html', true);
});

$$(document).on('click', '#submitInviteContactButton', function (e) {
    e.preventDefault();
    var contacts = [];
    $("#inviteContactList").find(".new-group-contact:checked").each(function (index, contact) {
        contacts.push(contactController.getContact($(contact).val()));
    });

    if(contacts.length <= 0) {
        return;
    }

    var groupId = $("#inviteContactGroupId").val();

    if (groupId === undefined || groupId === null) {
        return;
    }

    groupController.inviteUsersToGroup(groupId, contacts);
});

$$(document).on('open', '.popup-contact', function() {
    var event = {
        "eventType": "PageChange",
        "page": "ADDRESS_BOOK",
        "extra": ""
    };
    navigationController.dispatchEvent(event);
});

$$(document).on("click", "#addContactButton", function (e) {
    e.preventDefault();
    navigationController.loadPage('addContact.html', true);
});

$$(document).on("click", "#createGroupButton", function (e) {
    e.preventDefault();
    navigationController.loadPage('createGroup.html', true);
});

$$(document).on("click", "#loadProfileBtn", function (e) {
    navigationController.loadPage('profile.html', true);
});

$$(document).on("click", "#loadSettingsBtn", function (e) {
    navigationController.loadPage('settings.html', true);
});

$$(document).on("click", ".custom-dropdown", function (e) {
    var element = $$(this);
    var srcElement = $$(e.srcElement);
    if ($$(e.srcElement).hasClass('list-button') || srcElement.parents('a').hasClass('list-button')) {
        if (element.hasClass("custom-dropdown-toggled")) {
            element.removeClass("custom-dropdown-toggled");
        }
        else {
            element.addClass("custom-dropdown-toggled");
        }
    }
});

