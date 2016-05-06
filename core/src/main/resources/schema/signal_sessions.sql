CREATE TABLE IF NOT EXISTS signal_sessions(
    address TEXT PRIMARY KEY NOT NULL,
    session BLOB NOT NULL
)