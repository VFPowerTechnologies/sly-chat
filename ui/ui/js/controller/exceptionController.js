var ExceptionController = function () {
    this.activeDebug = true;
}

ExceptionController.prototype = {
    displayDebugMessage : function (exception) {
        if(this.activeDebug == true) {
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