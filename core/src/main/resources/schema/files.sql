CREATE TABLE IF NOT EXISTS files (
    id TEXT PRIMARY KEY NOT NULL,
    -- for sharing with other users
    share_key TEXT NOT NULL,
    -- 0 is used if the file has not been uploaded yet
    last_update_version INTEGER NOT NULL,
    is_deleted BOOLEAN NOT NULL,
    creation_date INTEGER NOT NULL,
    modification_date INTEGER NOT NULL,
    -- encrypted file size
    remote_file_size INTEGER NOT NULL,

    -- user metadata
    -- key used to encrypt file metadata and file data
    file_key BLOB NOT NULL,
    cipher_id INTEGER NOT NULL,
    file_name TEXT NOT NULL COLLATE NOCASE,
    directory TEXT NOT NULL COLLATE NOCASE,
    shared_from TEXT,

    -- file metadata
    chunk_size INTEGER NOT NULL,
    -- decrypted file size
    file_size INTEGER NOT NULL,

    is_pending BOOLEAN NOT NULL
);

CREATE INDEX files_idx_is_pending ON files (is_pending);
CREATE UNIQUE INDEX files_uniq_directory_file_name ON files (directory, file_name);