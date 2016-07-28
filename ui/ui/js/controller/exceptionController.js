var ExceptionController = function () {};

ExceptionController.prototype = {
    handleError : function (error) {
        console.log("An error occured");
        console.log(error);

        // if (window.buildConfig.DEBUG == true) {
        //     this.displayErrorModal(error);
        // }
        // else {
        //     console.log(error);
        // }

    },

    displayErrorModal : function (error) {
        slychat.modal({
            title:  error.message,
            text: error.stacktrace,
            buttons: [
                {
                    text: 'B1',
                    onClick: function() {
                    }
                }
            ]
        })
    }
};