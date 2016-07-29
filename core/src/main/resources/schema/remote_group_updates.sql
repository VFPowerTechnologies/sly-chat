-- List of current groups which need to be updated in the remote address book
CREATE TABLE IF NOT EXISTS remote_group_updates (
    group_id TEXT PRIMARY KEY,

    FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE
);