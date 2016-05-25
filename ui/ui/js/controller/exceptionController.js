var ExceptionController = function () {};

ExceptionController.prototype = {
    /**
     * Display the debug dialog if on DEBUG.
     *
     * @param exception
     */
    displayDebugMessage : function (exception) {
        if(window.buildConfig.DEBUG == true) {
            var modal = $("#exceptionModal");
            $("#exceptionName").text(exception.message);
            $("#debugStacktrace").text(exception.stacktrace);
            modal.openModal({
                dismissible: false
            });

            $(document).on("click", "#exceptionModalClose", function (e) {
                e.preventDefault();
                modal.closeModal();
            });
        }
    }
};