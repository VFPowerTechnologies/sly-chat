function Contacts() {
    this.contacts = [];
};
Contacts.prototype.setContacts = function(contacts){
    this.contacts = contacts;
    this.chatContact = null;
}
Contacts.prototype.getContacts = function(){
    return this.contacts;
}
Contacts.prototype.getContact = function(id){
    return this.contacts[id];
}
Contacts.prototype.fetchContact = function(){
    var contactsPromise = contactService.getContacts();
    contactsPromise.then(function(contacts){
        contacts.forEach(function(contact){
            this.contacts[contact.id] = contact;
        }.bind(this));
        this.showContacts();
    }.bind(this));
}
Contacts.prototype.displayContacts = function(){
    if(this.contacts.length <= 0){
        this.fetchContact();
    }
    else{
        this.showContacts();
    }
}
Contacts.prototype.setChatContact = function(id){
    this.chatContact = this.contacts[id];
    console.log("id : " + id);
    console.log("contact id : " + this.contacts[id].id);
}
Contacts.prototype.getChatContact = function(){
    return this.chatContact;
}
Contacts.prototype.showContacts = function(){
    var contactsBlock = "";
    this.contacts.forEach(function (contactDetails) {
        contactList.innerHTML += createContactBlock(contactDetails);
    });

    var links = document.getElementsByClassName("contact-link");
    for(var i = 0; i < links.length; i++){
        links[i].addEventListener("click", (function(self, id, link){
            return function(e) {
                self.setChatContact(id);
                loadPage("chat.html");
            };
        })(this, links[i].id.split("_")[1], links[i]));
    }
}