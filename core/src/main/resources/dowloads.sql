CREATE TABLE IF NOT EXISTS uploads (
    id TEXT PRIMARY KEY NOT NULL,
    file_id TEXT NOT NULL,
    local_path TEXT NOT NULL,

    decrypt BOOL NOT NULL,

    error TEXT DEFAULT NULL,

    FOREIGN KEY (file_id) REFERENCES files (id)
);
