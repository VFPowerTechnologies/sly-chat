CREATE TABLE IF NOT EXISTS conversation_info (
    contact_id INTEGER NOT NULL,
    unread_count INTEGER NOT NULL,
    last_message TEXT,
    -- unix time, in milliseconds
    last_timestamp INTEGER,

    FOREIGN KEY (contact_id) REFERENCES contacts (id)
)