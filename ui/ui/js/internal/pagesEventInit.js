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
        navigationController.loadPage('register.html', true);
    });

    if (isDesktop) {
        $("#login").focus();
    }
});

slychat.onPageInit('register', function (page) {
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
});

slychat.onPageInit('chat', function (page) {
    var isGroup = page.query.email === undefined;
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



    $$('#newMessageInput').on('keypress', function(e) {
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

    $("#submitNewChatMessage").click(function (e) {
        e.preventDefault();
        chatController.handleSubmitMessage(page.query);
    });

    $("#newMessageInputDiv").click(function () {
        $("#newMessageInput").focus();
    });

    $("#newMessageInput").focusout(function () {
        if ($(this).val() == "") {
            $(this).css("height", "13px");
            $(this).addClass("empty-textarea");
        }
        else
            $(this).removeClass("empty-textarea");
    });

    $("#inputChatExpireMessageBtn").click(function (e) {
        e.preventDefault();
        e.stopPropagation();
        chatController.toggleExpiringMessageDisplay();
    });

    if (isDesktop) {
        $("#newMessageInput").focus();
    }
});

slychat.onPageInit('addContact', function (page) {
    $$('#newContactSearchSubmit').on('click', function (e) {
        e.preventDefault();
        contactController.newContactSearch();
    });
});

slychat.onPageBeforeInit('contacts', function (page) {
    contactController.init();
    groupController.init();
});

slychat.onPageInit('smsVerification', function (page) {
    $$('#hiddenEmail').val(page.query.email);
    $$('#hiddenPassword').val(page.query.password);

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
                email: $$('#hiddenEmail').val()
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
        $("#contactPhone").html(contact.phoneNumber);
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

