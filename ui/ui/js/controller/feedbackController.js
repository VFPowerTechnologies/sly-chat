var FeedbackController = function () {};

FeedbackController.ids = {
    submitButton : '#submitFeedbackBtn',
    userInputFeedback : '#userInputFeedback',
    feedbackErrorsBlock : '#feedbackErrors'
};

FeedbackController.prototype = {
    pageInit : function () {
        this.addButtonListener();
    },

    addButtonListener : function () {
        $(FeedbackController.ids.submitButton).click(function (e) {
            e.preventDefault();
            this.handleSubmitClick();
        }.bind(this));
    },

    handleSubmitClick : function () {
        var input = $(FeedbackController.ids.userInputFeedback).val();

        if (input == null || input == '') {
            this.displayError("Feedback must not be empty");
        }
    },

    displayError : function (error) {
        $(FeedbackController.ids.feedbackErrorsBlock).html("<li>" + error +"</li>");
    },

    submitFeedback : function (feedback) {
        // feedbackService.submitFeedback(feedback).then(function () {
        //     //handleSuccess
        // }).catch(function (e) {
        //     exceptionController.handleError(e);
        // });
    }
};