CREATE TABLE IF NOT EXISTS groups (
    id INTEGER PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    -- whether or not the user has accepted the group
    is_pending INTEGER NOT NULL,
    -- 0: BLOCKED
    -- 1: PARTED
    -- 2: JOINED
    membership_level INTEGER NOT NULL
)