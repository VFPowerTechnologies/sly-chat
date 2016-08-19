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

//mouseheld event to trigger contact menu
(function($) {
    function startTrigger(e) {
        var $elem = $(this);
        $elem.data('mouseheld_timeout', setTimeout(function () {
            $elem.trigger('mouseheld');
        }, e.data));
    }

    function stopTrigger() {
        var $elem = $(this);
        clearTimeout($elem.data('mouseheld_timeout'));
    }


    var mouseheld = $.event.special.mouseheld = {
        setup: function (data) {
            var $this = $(this);
            $this.bind('mousedown', +data || mouseheld.time, startTrigger);
            $this.bind('mouseleave mouseup', stopTrigger);
        },
        teardown: function () {
            var $this = $(this);
            $this.unbind('mousedown', startTrigger);
            $this.unbind('mouseleave mouseup', stopTrigger);
        },
        time: 200
    };
})(jQuery);
