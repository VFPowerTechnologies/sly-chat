-- Contacts list
CREATE TABLE IF NOT EXISTS contacts (
    email TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    phone_number TEXT,
    public_key BLOB NOT NULL
)