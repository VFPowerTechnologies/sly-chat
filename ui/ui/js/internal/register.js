$(function(){
    document.getElementById("page-title").textContent = "Register";
    document.getElementById("name").focus();

    var height = window.innerHeight - 52;
    $("#content").css("min-height", height + "px");

    $("#networkStatus").addClass("hidden");
});
