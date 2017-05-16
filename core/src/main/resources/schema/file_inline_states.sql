CREATE TABLE file_inline_states (
    file_id TEXT PRIMARY KEY NOT NULL,
    is_inline BOOLEAN NOT NULL,

    FOREIGN KEY (file_id) REFERENCES files (id) ON DELETE CASCADE
);