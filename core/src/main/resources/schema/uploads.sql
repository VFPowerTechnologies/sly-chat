CREATE TABLE IF NOT EXISTS uploads (
    id TEXT PRIMARY KEY NOT NULL,
    file_id TEXT NOT NULL,
    state TEXT NOT NULL,
    -- file name as displayed in the upload dialog (needed mostly due to android content URIs)
    display_name TEXT NOT NULL,
    -- path of file on disk
    file_path TEXT NOT NULL,
    -- if true, don't encrypt file as it's already encrypted
    is_encrypted BOOLEAN NOT NULL,

    error TEXT DEFAULT NULL,

    FOREIGN KEY (file_id) REFERENCES files (id)
);