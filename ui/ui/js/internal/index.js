(function () {
    if (Framework7.prototype.device.android) {
        Dom7('head').append(
            '<link rel="stylesheet" href="css/framework7.material.min.css">' +
            '<link rel="stylesheet" href="css/framework7.material.colors.min.css">' +
            '<link rel="stylesheet" href="css/slychat.css">' +
            '<link rel="stylesheet" href="css/slychat.material.css">'
        );

        Dom7('.views').after(createMobileContactPopup());
    }
    else if (Framework7.prototype.device.ios) {
        Dom7('head').append(
            '<link rel="stylesheet" href="css/framework7.ios.min.css">' +
            '<link rel="stylesheet" href="css/framework7.ios.colors.min.css">' +
            '<link rel="stylesheet" href="css/slychat.css">' +
            '<link rel="stylesheet" href="css/slychat.ios.css">'
        );
        Dom7('.views').after(createMobileContactPopup());
    }
    else {
        Dom7('head').append(
            '<link rel="stylesheet" href="css/framework7.material.min.css">' +
            '<link rel="stylesheet" href="css/framework7.material.colors.min.css">' +
            '<link rel="stylesheet" href="css/slychat.css">' +
            '<link rel="stylesheet" href="css/slychat.desktop.css">'
        );

        Dom7('.views').prepend(createDesktopMenu());
    }
})();