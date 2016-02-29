CREATE TABLE IF NOT EXISTS conversation_info (
    contact_email TEXT NOT NULL,
    unread_count INTEGER NOT NULL,
    last_message TEXT,

    FOREIGN KEY (contact_email) REFERENCES contacts (email)
)