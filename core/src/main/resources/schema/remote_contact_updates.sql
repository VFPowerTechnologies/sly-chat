-- List of contacts
CREATE TABLE IF NOT EXISTS remote_contact_updates (
    -- no foreign key, as removed contacts are removed from the local list prior to remote update
    contact_id INTEGER PRIMARY KEY NOT NULL,
    -- see RemoteContactModificationType
    type TEXT NOT NULL
)
