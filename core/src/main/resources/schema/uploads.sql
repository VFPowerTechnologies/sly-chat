CREATE TABLE IF NOT EXISTS uploads (
    id TEXT PRIMARY KEY NOT NULL,
    -- TODO
    state INTEGER NOT NULL,
    -- path of file on disk
    file_path TEXT NOT NULL,
    -- if true, don't encrypt file as it's already encrypted
    is_encrypted BOOLEAN NOT NULL,

    -- preserialized for convinence
    user_metadata BLOB NOT NULL,
    file_metadata BLOB NOT NULL
);