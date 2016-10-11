var ExceptionController = function () {};

ExceptionController.prototype = {
    handleError : function (error) {
        console.log("An error occured");
        console.log(error);

        if (window.buildConfig.DEBUG == true) {
            this.displayErrorModal(error);
        }
        else {
            console.log(error);
        }

    },

    displayErrorModal : function (error) {
        var navbar = '' +
            '<div class="navbar top-navbar">' +
            '<div class="navbar-inner">' +
            '<a href="#" class="link close-popup close-popup-btn icon-only" style="color: #ff5722;"> Close </a>An error occured' +
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
            error.message + '</div>' +
            '<div class="content-block">' +
            error.stacktrace +
            '</div></div></div></div></div></div>';

        slychat.popup(popupHTML);
    }
};