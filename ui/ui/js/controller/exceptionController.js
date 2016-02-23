var ExceptionController = function () {};

ExceptionController.prototype = {
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
}