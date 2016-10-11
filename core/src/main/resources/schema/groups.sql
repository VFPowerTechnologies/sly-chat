CREATE TABLE IF NOT EXISTS groups (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    -- 0: BLOCKED
    -- 1: PARTED
    -- 2: JOINED
    membership_level INTEGER NOT NULL
)