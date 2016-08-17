-- +address_book_remote_version

CREATE TABLE IF NOT EXISTS address_book_version (
    version INTEGER NOT NULL
);

INSERT INTO address_book_version (version) VALUES (0);