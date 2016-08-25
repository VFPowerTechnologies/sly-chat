-- contacts/groups are never deleted once added, so we don't need to bother trying to link them
CREATE TABLE IF NOT EXISTS address_book_hashes (
    -- RemoteAddressBookEntry.hash
    id_hash BLOB PRIMARY KEY NOT NULL,
    -- md5(RemoteAddressBookEntry.encryptedData)
    data_hash BLOB NOT NULL
);
