// Login page init function
slychat.onPageInit('login', function (page) {
    navigationController.hideSplashScreen();

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
});

// Register page init function
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
});

// Chat page init function
slychat.onPageInit('chat', function (page) {
    if (page.query.email !== undefined) {
        chatController.fetchMessageFor(0, 100, page.query);
        chatController.markConversationAsRead(contactController.getContact(page.query.id));
    }
    else {
        groupController.fetchGroupMessage(0, 100, page.query.id);
        groupController.markGroupConversationAsRead(page.query.id);
    }

    $$('#contact-name').html(page.query.name);
    $$('#contact-id').html(page.query.id);

    $$('#newMessageInput').on('keypress', function(e) {
        if  (e.keyCode === 13 && !e.ctrlKey && !e.shiftKey) {
            e.preventDefault();
            var message = $$(this).val();
            if(message !== "")
                chatController.submitNewMessage(page.query, message);
        }

        if ($(this).val() == "")
            $(this).addClass("empty-textarea");
        else
            $(this).removeClass("empty-textarea");
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
});

// Add Contact page init function
slychat.onPageInit('addContact', function (page) {
    $$('#newContactSearchSubmit').on('click', function (e) {
        e.preventDefault();
        contactController.newContactSearch();
    });
});

// Contact page init function
slychat.onPageBeforeInit('contacts', function (page) {
    contactController.init();
});

// Sms Verification page init function
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

// Update Phone page init function
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

// Profile page init function
slychat.onPageInit('profile', function (page) {
    profileController.displayInfo();

    $$('#profileEmailLink').on('click', function () {
        profileController.openEmailUpdateForm();
    });

    $$('#profileNameLink').on('click', function () {
        profileController.openNameUpdateForm();
    });

    $$('#profilePhoneLink').on('click', function () {
        profileController.openPhoneUpdateForm();
    });
});

slychat.onPageInit('createGroup', function (page) {
    groupController.insertContactList();

    $("#createNewGroupBtn").click(function (e) {
        e.preventDefault();
        groupController.createGroup();
    })
});

// Contact popup event
$$('#addNewContactGoBtn').on('click', function (e) {
    navigationController.loadPage('addContact.html', true);
});

$$(document).on("input", ".invalid-required", function(e) {
    if ($(this).val().trim() !== "") {
        $(this).removeClass("invalid-required");
    }
    else {
        $(this).addClass("invalid-required");
    }
});

$(window).resize(function () {
    if($$('#mainView').data('page') == "chat")
        chatController.scrollTop();
});

// Global event
$$(document).on('click', '.backBtn', function () {
    navigationController.goBack();
});

$$(document).on('click', '#logoutBtn', function (e) {
    e.preventDefault();
    loginController.logout();
});

$$(document).on('click', '#profileBtn', function () {
    navigationController.loadPage('profile.html', true);
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

// TODO Implement left contact menu in chat page for easy switching between contact
// var mc = new Hammer(document.getElementById('mainView'));
// mc.on("swipeleft swiperight", function (e) {
//     if(navigationController.getCurrentPage() == "chat.html") {
//         if (e.type == "swiperight")
//             console.log(e.type + " gesture detected");
//         else if (e.type == "swipeleft")
//             console.log(e.type + " gesture detected");
//     }
// });