-- Contacts list
CREATE TABLE IF NOT EXISTS contacts (
    id INTEGER NOT NULL UNIQUE,
    email TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    phone_number TEXT,
    public_key BLOB NOT NULL
)