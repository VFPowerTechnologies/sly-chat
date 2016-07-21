CREATE TABLE IF NOT EXISTS group_conversation_info (
    group_id TEXT NOT NULL,
    last_speaker_contact_id INTEGER,
    unread_message_count INTEGER NOT NULL,
    last_message TEXT,
    -- unix time, in milliseconds
    last_timestamp INTEGER,

    FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    -- there's no sane way to handle this; in normal app operation a contacts table entry will never be removed once
    -- added, so this isn't a problem
    FOREIGN KEY (last_speaker_contact_id) REFERENCES contacts (id) ON DELETE SET NULL
)