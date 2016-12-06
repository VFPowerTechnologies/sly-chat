function createDesktopMenu () {
    return '<div class="panel-overlay"></div>' +
        '<div id="leftMenuPanel" class="panel panel-left panel-cover">' +
        '<div class="view view-left">' +
        '<div class="pages">' +
        '<div data-page="contact-left" class="page">' +
        '<div class="page-content">' +
        '<div class="list-block" style="margin-top: 5px;">' +
                '<ul class="dropdown-menu no-close">' +
                    '<li>' +
                        '<a href="#" id="leftMenuProfileName"></a>' +
                        '<ul style="box-sizing: border-box; padding: 10px; margin-left: 20px; width: 225px;">' +
                            '<li id="leftMenuUserInfo" class="no-close"></li>' +
                            '<li><a href="#" onclick="navigationController.loadPage(\'contacts.html\');">Home</a></li>' +
                            '<li><a onclick="navigationController.loadPage(\'blockedContacts.html\');" href="#">Blocked Contacts</a></li>' +
                            '<li><a id="loadProfileBtn" href="#">Profile</a></li>' +
                            '<li><a id="loadSettingsBtn" href="#">Settings</a></li>' +
                            '<li><a id="loadSettingsBtn" href="#" onclick="navigationController.loadPage(\'feedback.html\', true);">Send Feedback</a></li>' +
                            '<li><a id="logoutBtn" href="#">Logout</a></li>' +
                        '</ul>' +
                    '</li>' +
                '</ul>' +
                '<ul>' +
                    '<li style="margin-bottom: 25px;">' +
                        '<div class="content-title" style="padding-left: 16px; margin-bottom: 5px; color: #fff;">Contacts' +
                            '<a href="#" id="addContactButton" style="float: right; margin-right: 10px;"><i class="fa fa-plus-circle"></i></a>' +
                        '</div>' +
                        '<div>' +
                            '<ul id="leftContactList" style="padding-left: 21px; font-size: 14px;">' +
                            '</ul>' +
                        '</div>' +
                    '</li>' +
                    '<li style="margin-bottom: 25px;">' +
                        '<div class="content-title" style="padding-left: 16px; margin-bottom: 5px; color: #fff;">Groups' +
                            '<a href="#" id="createGroupButton" style="float: right; margin-right: 10px;"><i class="fa fa-plus-circle"></i></a>' +
                        '</div>' +
                        '<div>' +
                            '<ul id="leftGroupList" style="padding-left: 21px; font-size: 14px;">' +
                            '</ul>' +
                        '</div>' +
                    '</li>' +
                '</ul>' +
            '</div>' +
        '</div></div></div></div></div>';
}

function createMobileContactPopup(isIos) {
    var navbar = '<div class="navbar top-navbar">' +
        '<div class="navbar-inner">' +
        '<div class="left">' +
        '<a href="#" class="link close-popup close-popup-btn icon-only"> <i class="icon icon-back" style="margin-left: 10px;"></i></a>Address Book' +
        '</div><div class="right">';
    if(isIos)
        navbar += '<a id="contactPopupNewBtn" href="#" class="link icon-only close-popup"> <i class="fa fa-plus-square"></i></a>';

    navbar += '</div></div></div>';

    var addContactFloatingBtn = "";
    if(!isIos)
        addContactFloatingBtn = '<a href="#" id="contactPopupNewBtn" class="floating-button close-popup">new</a>';

    return '<div class="popup-overlay"></div>' +
        '<div id="contactPopup" class="popup popup-contact tablet-fullscreen">' +
        '<div class="view navbar-fixed" data-page>' +
        '<div class="pages">' +
        '<div data-page class="page">' +
        navbar +
        '<div class="page-content" style="padding-bottom: 48px;">' +
        '<div class="tabs-swipeable-wrap">' +
        addContactFloatingBtn +
        '<div class="tabs">' +
        '<div class="tab active" id="contact-tab" style="overflow: auto;">' +
        '<div class="content-block-title">Recent contacts</div>' +
        '<div id="recentContactList" class="content-block recent-contact-list"></div>' +
        '<div class="content-block-title">Contacts</div>' +
        '<div class="content-block list-block">' +
        '<ul id="contact-list"></ul></div></div>' +
        '<div class="tab" id="group-tab" style="overflow: auto;">' +
        '<div class="content-block-title">Groups</div>' +
        '<div class="content-block">' +
        '<div class="content-block">' +
        '<div id="groupList" class="row"></div>' +
        '</div></div></div></div></div></div>' +
        '<div class="toolbar toolbar-bottom tabbar" style="color:  #fff; line-height: 48px;">' +
        '<div class="toolbar-inner">' +
        '<a href="#contact-tab" class="tab-link active">Contacts</a>' +
        '<a href="#group-tab" class="tab-link">Groups</a>' +
        '</div></div></div></div></div></div>';
}

function createIosMenu() {
    var existingMenu = $("#iosMenu");
    if (existingMenu.length <= 0) {
        var inviteFriends = "";
        if (window.shareSupported) {
            inviteFriends = '<li id="menuInviteFriendsLink" class="item-content close-panel">' +
                '<div class="item-media"><i class="fa fa-share-alt"></i></div>' +
                '<div class="item-inner">' +
                '<div class="item-title">Invite Friends</div>' +
                '</div>' +
                '</li>';
        }
        var menu = $('<div class="panel-overlay"></div>' +
            '<div id="iosMenu" class="panel panel-right panel-cover">' +
            '<div class="ios-menu-header" style="min-height: 100px; text-align: center; padding-bottom: 5px; border-bottom: 1px solid #eee;">' +
            '<div style="height: 80px;">' +
            '<img style="height: 80px; width: 80px; display: block; margin: auto;" src="img/sly_logo.png"/>' +
            '</div>' +
            '<p id="iosMenuUserName" style="color: #fff; margin: 0 10px;">' + profileController.name + '</p>' +
            '<p id="iosMenuUserEmail" style="color: #fff; margin: 0 10px;">' + profileController.username + '</p>' +
            '</div>' +
            '<div class="list-block">' +
            '<ul id="iosMenuList">' +
            '<li id="menuProfileLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-user"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Profile</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuSettingsLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-cogs"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Settings</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuAddContactLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-user-plus"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Add Contact</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuCreateGroupLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-users"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Create Group</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuBlockedContactsLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-ban"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Blocked Contacts</div>' +
            '</div>' +
            '</li>' +
            inviteFriends +
            '<li id="menuFeedbackLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-commenting"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Feedback</div>' +
            '</div>' +
            '</li>' +
            '<li id="menuLogoutLink" class="item-content close-panel">' +
            '<div class="item-media"><i class="fa fa-sign-out"></i></div>' +
            '<div class="item-inner">' +
            '<div class="item-title">Logout</div>' +
            '</div>' +
            '</li>' +
            '</ul>' +
            '</div>' +
            '</div>');

        menu.find("#menuProfileLink").click(function () {
            navigationController.loadPage('profile.html', true);
        });

        menu.find("#menuSettingsLink").click(function () {
            navigationController.loadPage('settings.html', true);
        });

        menu.find("#menuBlockedContactsLink").click(function () {
            navigationController.loadPage('blockedContacts.html', true);
        });

        menu.find("#menuAddContactLink").click(function () {
            navigationController.loadPage("addContact.html", true);
        });

        menu.find("#menuCreateGroupLink").click(function () {
            navigationController.loadPage('createGroup.html', true);
        });

        menu.find("#menuInviteFriendsLink").click(function () {
            navigationController.loadPage("inviteFriends.html", true);
        });

        menu.find("#menuFeedbackLink").click(function () {
            navigationController.loadPage("feedback.html", true);
        });

        menu.find("#menuLogoutLink").click(function () {
            loginController.logout();
        });

        $('body').prepend(menu);
    }
    else {
        existingMenu.find("#iosMenuUserName").html(profileController.name);
        existingMenu.find("#iosMenuUserEmail").html(profileController.username);
    }
}

function formatPublicKey(publicKey) {
    var publicKeyArr = publicKey.match(/.{1,4}/g);
    var formated = '';
    publicKeyArr.forEach(function (item, index) {
        if(index > publicKeyArr.length - 4)
            formated += " <span style='color: red'>" + item + "</span>";
        else
            formated += item + " ";
    });

    return formated;
}

Object.size = function (obj) {
    var size = 0;
    for (var key in obj) {
        if (obj.hasOwnProperty(key)) size++;
    }

    return size;
};

function vibrate (delay) {
    var supportsVibrate = "vibrate" in navigator;

    if(supportsVibrate)
        navigator.vibrate(delay);
}

function copyToClipboard (message) {
    windowService.copyTextToClipboard(message).then(function () {
        console.log("message has been copied");
    }.bind(this)).catch(function (e) {
        // TODO handle errors
        console.log("An error occured, could not copy message to clipboard.");
    })
}

function openInfoPopup (content, title) {
    if (title === undefined)
        title = "";

    var navbar = '' +
        '<div class="navbar top-navbar">' +
            '<div class="navbar-inner">' +
                '<a href="#" class="link close-popup close-popup-btn icon-only"> <i class="icon icon-back" style="margin-left: 10px;"></i></a>' + title +
            '</div>' +
        '</div>';

    var popupHTML = '' +
        '<div class="popup info-popup tablet-fullscreen">' +
            '<div class="view navbar-fixed" data-page>' +
                '<div class="pages">' +
                    '<div data-page class="page">' +
                        navbar +
                        '<div class="page-content">'+
                            '<div class="content-block">' +
                                content +
                            '</div>' +
                        '</div>' +
                    '</div>' +
                '</div>' +
            '</div>' +
        '</div>';

    slychat.popup(popupHTML);
}

function linkify(content) {
    var linkDetectionRegex = /(([a-zA-Z]+:\/\/)?(([a-zA-Z0-9\-]+\.)+([a-z]{2}|aero|arpa|biz|com|coop|edu|gov|info|int|jobs|mil|museum|name|nato|net|org|pro|travel|local|internal))(:[0-9]{1,5})?(\/[a-z0-9_\-\.~]+)*(\/([a-z0-9_\-\.]*)(\?[a-z0-9+_\-\.%=&amp;]*)?)?(#[a-zA-Z0-9!$&'()*+.=-_~:@/?]*)?)(\s+|$)/;

    return content.replace(linkDetectionRegex, function (url) {
        var address;
        address = /[a-zA-Z]+:\/\//.test(url) ? url : "http://" + url;
        return "<a class='chatLink' href='#' data-href='" + address + "' style='text-decoration: underline'>" + url + "</a>";
    });
}

function formatTextForHTML(content) {
    var safeContent = linkify(content);
    safeContent = safeContent.replace(/\n/g, '<br/>');
    return formatEmoji(safeContent);
}

function formatEmoji(text) {
    return emojione.toImage(text);
}

function createTextNode (string) {
    var pre = document.createElement("pre");
    var text = document.createTextNode(string);
    pre.appendChild(text);
    return pre.innerHTML;
}
