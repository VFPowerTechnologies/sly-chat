-- Contacts list
-- Once a contact has been interned, it's never removed from this table; only its message level will change.
CREATE TABLE IF NOT EXISTS contacts (
    id INTEGER PRIMARY KEY NOT NULL,
    email TEXT NOT NULL,
    name TEXT NOT NULL,
    -- see AllowedMessageLevel
    -- 0: blocked
    -- 1: group-only (default for auto-add)
    -- 2: all
    allowed_message_level INTEGER NOT NULL,
    public_key TEXT NOT NULL
)