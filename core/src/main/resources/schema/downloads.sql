CREATE TABLE downloads (
    id TEXT PRIMARY KEY NOT NULL,
    file_id TEXT NOT NULL,
    state TEXT NOT NULL,
    -- path of file on disk
    file_path TEXT NOT NULL,
    -- if true, store as decrypted; set to false when downloading image attachments
    do_decrypt BOOLEAN NOT NULL,

    error TEXT DEFAULT NULL,

    FOREIGN KEY (file_id) REFERENCES files (id)
);