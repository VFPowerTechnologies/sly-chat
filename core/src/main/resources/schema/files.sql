CREATE TABLE IF NOT EXISTS files (
    file_id TEXT PRIMARY KEY NOT NULL,
    -- for sharing with other users
    share_key TEXT NOT NULL,
    -- XXX maybe?
    last_update_version INTEGER NOT NULL,
    is_deleted BOOLEAN NOT NULL,
    creation_date INTEGER NOT NULL,
    modification_date INTEGER NOT NULL,
    -- encrypted file size
    remote_file_size INTEGER NOT NULL,

    -- user metadata
    -- key used to encrypt file metadata and file data
    file_key TEXT NOT NULL,
    file_name TEXT NOT NULL,
    directory TEXT NOT NULL,
    shared_from TEXT NOT NULL,

    -- file metadata
    cipher_id INTEGER NOT NULL,
    chunk_size INTEGER NOT NULL,
    -- decrypted file size
    file_size INTEGER NOT NULL
);