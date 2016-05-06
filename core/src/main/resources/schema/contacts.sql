-- Contacts list
CREATE TABLE IF NOT EXISTS contacts (
    id INTEGER PRIMARY KEY NOT NULL,
    email TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    phone_number TEXT,
    public_key BLOB NOT NULL
)