slychat.validateForm = function (form) {
    var functions = {
        parseRules : function (rules) {
            return rules.split("|");
        },

        required : function (input) {
            if (input.val() === null || input.val().trim() === "") {
                functions.displayError(input, input.attr("data-errorName") + " is required");
                return false;
            }
            else
                return true;
        },

        confirmed : function (input) {
            var confInput = $("#" + input.attr("data-confirmationId"));
            if (confInput.length > 0) {
                if (confInput.val() != input.val()) {
                    functions.displayError(input, "Password does not match");
                    functions.displayError(confInput, "Password does not match");

                    return false;
                }
                else
                    return true;
            }
            else
                return false;
        },

        validateSize : function (input, min) {
            var length = input.val().length;
            if (length > 0 && length < min) {
                functions.displayError(input, input.attr("data-errorName") + " length must be minimum 6 characters");
                return false;
            }
            else
                return true;
        },

        email : function (input) {
            if (input.val().trim() !== "" && validateEmail(input.val()) === false) {
                functions.displayError(input, input.attr("data-errorName") + " must be a valid email");
                return false;
            }
            else
                return true;
        },

        displayError : function (input, error) {
            var parent = input.parents("li");
            if (parent.find(".invalid-details").length <= 0)
                parent.append("<div class='invalid-details'>" + error + "</div>");
            else
                parent.find(".invalid-details").append($(this).attr("data-errorName") + " is required");
        },

        phone : function (input) {
            if (input.val().trim() !== "" && validatePhone(input.val(), $("#countrySelect").val()) === false) {
                functions.displayError(input, input.attr("data-errorName") + " does not seem to be valid");
                return false;
            }
            else
                return true;
        }
    };

    function validate (input, rules) {
        var fn;
        var valid = true;
        rules.forEach(function (rule) {
            fn = functions[rule];
            if (typeof fn === 'function') {
                if (fn(input) === false)
                    valid = false;
            }
            else if (rule.search("min") >= 0) {
                var min = parseInt(rule.split("-")[1]);
                if (functions.validateSize(input, min) === false)
                    valid = false;
            }
        });

        return valid;
    }

    var valid = true;
    $(".invalid-details").remove();

    var items = form.find($(".form-input-validate"));
    $(items).each(function () {
        var rules = functions.parseRules($(this).attr("data-rules"));

        if (validate($(this), rules) === false) {
            valid = false;
            $(this).addClass("invalid-required");
            $(this).parents("li").addClass("invalid");
        }
        else {
            $(this).parents("li").removeClass("invalid");
            $(this).removeClass("invalid-required");
        }
    });

    return valid;
};

function validateEmail(input) {
    var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
    return re.test(input);
}