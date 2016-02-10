function Contacts() {
    this.conversations = [];
    this.chatContact = null;
};

Contacts.prototype.getContact = function(id){
    return this.conversations[id].contact;
};

Contacts.prototype.setContacts = function(contacts){
    this.conversations = [];
};

Contacts.prototype.fetchConverstations = function(){
    var messengerPromise = messengerService.getConversations();
    messengerPromise.then(function(conversations){
        conversations.forEach(function(conversation){
            this.conversations[conversation.contact.id] = conversation;
        }.bind(this));
        this.showContacts();
    }.bind(this)).catch(function(e){
        console.log("Unable to fetch conversations: " + e);
    });
}

Contacts.prototype.displayContacts = function(){
    if(this.conversations.length <= 0){
        this.fetchConverstations();
    }
    else{
        this.showContacts();
    }
}
Contacts.prototype.setChatContact = function(id){
    this.chatContact = this.conversations[id].contact;
}
Contacts.prototype.getChatContact = function(){
    return this.chatContact;
}
Contacts.prototype.showContacts = function(){
    contactList = document.getElementById("contactList");
    contactList.innerHTML = "";

    for(var i = 0; i < this.conversations.length; i++){
        contactList.innerHTML += createContactBlock(this.conversations[i].contact, this.conversations[i].status);
    }

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