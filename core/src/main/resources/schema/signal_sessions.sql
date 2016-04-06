CREATE TABLE IF NOT EXISTS signal_sessions(
    address TEXT NOT NULL UNIQUE,
    session BLOB NOT NULL
)