CREATE TABLE remote_file_updates (
    file_id TEXT PRIMARY KEY NOT NULL,

    -- d: delete
    -- m:
    type TEXT NOT NULL,

    FOREIGN KEY (file_id) REFERENCES files (id)
);