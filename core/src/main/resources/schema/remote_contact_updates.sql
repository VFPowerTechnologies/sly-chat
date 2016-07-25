-- List of contact list updates not yet pushed to remote servers
CREATE TABLE IF NOT EXISTS remote_contact_updates (
    contact_id INTEGER PRIMARY KEY NOT NULL,
    -- see AllowedMessageLevel
    allowed_message_level INTEGER NOT NULL,

    FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE
)
