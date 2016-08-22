function createDesktopMenu () {
    return '<div class="panel-overlay"></div>' +
        '<div id="leftMenuPanel" class="panel panel-left panel-cover">' +
        '<div class="view view-left navbar-through">' +
        '<div class="pages">' +
        '<div data-page="contact-left" class="page" style="background-color: #eee;">' +
        '<div class="navbar"><div class="navbar-inner"><div class="left"><img style="height: 40px; width: 40px; margin-right: 10px; padding-left: 10px;" src="img/sly_logo.png"> Sly Chat </div></div></div>' +
        '<div class="page-content">' +
        '<div class="list-block" style="margin-top: 5px;">' +
        '<ul>' +
        '<li class="accordion-item"><a href="#" class="item-content item-link">' +
        '<div class="item-inner">' +
        '<div id="leftDesktopProfileName" class="item-title"></div>' +
        '</div></a>' +
        '<div class="accordion-item-content">' +
        '<div class="content-block" style="margin-left: 5px;">' +
        '<div class="list-block">' +
        '<ul>' +
        '<li><a id="loadProfileBtn" href="#">Profile</a></li>' +
        '<li><a id="logoutBtn" href="#">Logout</a></li>' +
        '</ul></div></div></div></li>' +
        '<li class="accordion-item accordion-item-expanded"><a href="#" class="item-content item-link">' +
        '<div class="item-inner">' +
        '<div class="item-title">Contacts</div>' +
        '</div></a>' +
        '<div class="accordion-item-content">' +
        '<div class="content-block" style="margin-left: 5px;">' +
        '<div class="list-block">' +
        '<div style="width: 100%; text-align: right;">' +
        '<a href="#" id="addContactButton" style="font-size: 12px; color: blue;">Add contact</a>' +
        '</div>' +
        '<ul id="leftContactList">' +
        '</ul></div></div></div></li>' +
        '<li class="accordion-item"><a href="#" class="item-content item-link">' +
        '<div class="item-inner">' +
        '<div class="item-title">Groups</div>' +
        '</div></a>' +
        '<div class="accordion-item-content">' +
        '<div class="content-block" style="margin-left: 5px;">' +
        '<div class="list-block">' +
        '<div style="width: 100%; text-align: right;">' +
        '<a href="#" id="createGroupButton" style="font-size: 12px; color: blue;">New group</a>' +
        '</div>' +
        '<ul id="leftGroupList"></ul>' +
        '</div></div></div></li></ul></div></div></div></div></div></div>';
}

function createMobileContactPopup() {
    return '<div class="popup-overlay"></div>' +
        '<div id="contactPopup" class="popup popup-contact tablet-fullscreen">' +
        '<div class="view navbar-fixed" data-page>' +
        '<div class="pages">' +
        '<div data-page class="page">' +
        '<div class="navbar top-navbar">' +
        '<div class="navbar-inner">' +
        '<div class="left">' +
        '<a href="#" class="link close-popup close-popup-btn icon-only"> <i class="icon icon-back" style="margin-left: 10px;"></i></a>Address Book' +
        '</div><div class="right"></div></div></div>' +
        '<div class="page-content" style="padding-bottom: 48px;">' +
        '<div class="tabs-swipeable-wrap">' +
        '<a href="#" id="contactPopupNewBtn" class="floating-button close-popup">new</a>' +
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

function validateEmail(email) {
    var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(email);
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

function parseFormatedTimeString (timestamp) {
    return new Date(timestamp);
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
    return safeContent;
}

function createTextNode (string) {
    var pre = document.createElement("pre");
    var text = document.createTextNode(string);
    pre.appendChild(text);
    return pre.innerHTML;
}
