CREATE TABLE IF NOT EXISTS signal_sessions(
    contact_id INTEGER NOT NULL,
    device_id INTEGER NOT NULL,
    session BLOB NOT NULL,

    PRIMARY KEY (contact_id, device_id),
    FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE
)