CREATE TABLE IF NOT EXISTS conversation_info (
    conversation_id TEXT PRIMARY KEY,
    last_speaker_contact_id INTEGER,
    unread_count INTEGER NOT NULL,
    -- this may be null if the most recent message is expirable
    last_message TEXT,
    -- unix time, in milliseconds
    -- this will never be null if there is a most recent message
    last_timestamp INTEGER,

    -- there's no sane way to handle this; in normal app operation a contacts table entry will never be removed once
    -- added, so this isn't a problem
    FOREIGN KEY (last_speaker_contact_id) REFERENCES contacts (id) ON DELETE SET NULL
)