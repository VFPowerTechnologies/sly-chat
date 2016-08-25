-- -address_book_remote_version

DROP TABLE IF EXISTS address_book_version;

CREATE TABLE IF NOT EXISTS address_book_hashes (
    id_hash BLOB PRIMARY KEY NOT NULL,
    data_hash BLOB NOT NULL
);
