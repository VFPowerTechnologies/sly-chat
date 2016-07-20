CREATE TABLE IF NOT EXISTS group_members (
    group_id INTEGER NOT NULL,
    contact_id INTEGER NOT NULL,

    FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE
)