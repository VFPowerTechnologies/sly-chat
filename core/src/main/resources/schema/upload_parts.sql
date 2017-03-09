CREATE TABLE IF NOT EXISTS upload_parts (
    upload_id TEXT NOT NULL,
    -- [1, ...]
    n INTEGER NOT NULL,
    offset INTEGER NOT NULL,
    -- this is the local file size that makes up this upload part
    local_size INTEGER NOT NULL,
    -- this is the encrypted size that gets uploaded
    remote_size INTEGER NOT NULL,
    is_complete BOOLEAN NOT NULL,

    FOREIGN KEY (upload_id) REFERENCES uploads (id) ON DELETE CASCADE,
    UNIQUE (upload_id, n)
);