-- +remote_group_updates
-- -remote_contact_updates.allowed_message_level

ALTER TABLE remote_contact_updates RENAME TO remote_contact_updates_old;

CREATE TABLE IF NOT EXISTS remote_contact_updates (
    contact_id INTEGER PRIMARY KEY NOT NULL,

    FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE
);

INSERT INTO remote_contact_updates (contact_id) SELECT contact_id FROM remote_contact_updates_old;

DROP TABLE remote_contact_updates_old;

CREATE TABLE IF NOT EXISTS remote_group_updates (
    group_id TEXT PRIMARY KEY,

    FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE
);
