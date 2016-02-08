function Contacts() {
    this.contacts = [];
    this.lastMessage = [];
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
            this.fetchLastMessage(contact);
        }.bind(this));
    }.bind(this));
}

Contacts.prototype.fetchLastMessage = function(contact){
    messengerService.getLastMessagesFor(contact, 0, 1).then(function (messages) {
        this.lastMessage[contact.id] = messages[0];
        this.showContacts();
    }.bind(this)).catch(function (e) {
        console.error("Unable to fetch last message: " + e);
    });
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
}
Contacts.prototype.getChatContact = function(){
    return this.chatContact;
}
Contacts.prototype.showContacts = function(){
    contactList = document.getElementById("contactList");
    contactList.innerHTML = "";
    this.contacts.forEach(function (contactDetails) {
        contactList.innerHTML += createContactBlock(contactDetails, this.lastMessage[contactDetails.id]);
    }.bind(this));

    var links = document.getElementsByClassName("contact-link");
    for(var i = 0; i < links.length; i++){
        links[i].addEventListener("click", (function(self, id, link){
            return function(e) {
                e.preventDefault();
                self.setChatContact(id);
                loadPage("chat.html");
            };
        })(this, links[i].id.split("_")[1], links[i]));
    }
}