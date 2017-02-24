CREATE TABLE IF NOT EXISTS upload_parts (
    upload_id TEXT NOT NULL,
    -- [1, ...]
    n INTEGER NOT NULL,
    size INTEGER NOT NULL,
    is_complete BOOLEAN NOT NULL,

    FOREIGN KEY (upload_id) REFERENCES uploads (id) ON DELETE CASCADE,
    UNIQUE (upload_id, n)
);