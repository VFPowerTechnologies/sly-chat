-- Contacts list
CREATE TABLE IF NOT EXISTS contacts (
    id INTEGER PRIMARY KEY NOT NULL,
    email TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    -- 0: blocked
    -- 1: group-only (default for auto-add)
    -- 2: all
    allowed_message_level INTEGER NOT NULL,
    -- whether or not the user has accept the contact
    is_pending INTEGER NOT NULL,
    phone_number TEXT,
    public_key BLOB NOT NULL
)