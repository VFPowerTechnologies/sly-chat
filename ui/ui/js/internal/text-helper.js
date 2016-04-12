function linkify(content) {
    var linkDetectionRegex = /(([a-zA-Z]+:\/\/)?(([a-zA-Z0-9\-]+\.)+([a-z]{2}|aero|arpa|biz|com|coop|edu|gov|info|int|jobs|mil|museum|name|nato|net|org|pro|travel|local|internal))(:[0-9]{1,5})?(\/[a-z0-9_\-\.~]+)*(\/([a-z0-9_\-\.]*)(\?[a-z0-9+_\-\.%=&amp;]*)?)?(#[a-zA-Z0-9!$&'()*+.=-_~:@/?]*)?)(\s+|$)/;

    return content.replace(linkDetectionRegex, function (url) {
        var address;
        address = /[a-zA-Z]+:\/\//.test(url) ? url : "http://" + url;
        return "<a class='chatLink' href='" + address + "'>" + url + "</a>";
    });
}

function formatTextForHTML(content) {
    var safeContent = linkify(content);
    safeContent = safeContent.replace(/\n/g, '<br/>');
    return safeContent;
}