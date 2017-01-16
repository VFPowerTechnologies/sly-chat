(function () {
    if (Framework7.prototype.device.android) {
        Dom7('head').append(
            '<link rel="stylesheet" href="css/framework7.material.min.css">' +
            '<link rel="stylesheet" href="css/framework7.material.colors.min.css">' +
            '<link rel="stylesheet" href="css/slychat.css">' +
            '<link rel="stylesheet" href="css/slychat.material.css">'
        );

        $('.views').after(createMobileContactPopup(false));
    }
    else if (Framework7.prototype.device.ios) {
        Dom7('head').append(
            '<link rel="stylesheet" href="css/framework7.ios.min.css">' +
            '<link rel="stylesheet" href="css/framework7.ios.colors.min.css">' +
            '<link rel="stylesheet" href="css/slychat.css">' +
            '<link rel="stylesheet" href="css/slychat.ios.css">'
        );
        $('.views').after(createMobileContactPopup(true));
    }
    else {
        Dom7('head').append(
            '<link rel="stylesheet" href="css/framework7.material.min.css">' +
            '<link rel="stylesheet" href="css/framework7.material.colors.min.css">' +
            '<link rel="stylesheet" href="css/slychat.css">' +
            '<link rel="stylesheet" href="css/slychat.desktop.css">'
        );

        $('.views').prepend(createDesktopMenu());

        $(document).ready(function() {
            $('.dropdown-menu').dropit();
        });
    }
})();

if (typeof Promise === 'undefined')
    $("body").prepend("<script src='js/external-lib/promise.min.js'></script>");
