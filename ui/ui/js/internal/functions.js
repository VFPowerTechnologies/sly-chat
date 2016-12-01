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
