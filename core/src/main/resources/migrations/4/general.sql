ALTER TABLE signal_sessions RENAME TO signal_sessions_old;
CREATE TABLE IF NOT EXISTS signal_sessions(
    contact_id INTEGER KEY NOT NULL,
    device_id INTEGER NOT NULL,
    session BLOB NOT NULL,

    PRIMARY KEY (contact_id, device_id),
    FOREIGN KEY (contact_id) REFERENCES contacts (id)
);
-- the data transfer and table drop is done in code; see DatabaseMigration4
