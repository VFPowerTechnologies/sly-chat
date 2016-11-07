CREATE TABLE IF NOT EXISTS event_log (
    -- NULL for system messages
    conversation_id TEXT,
    type TEXT NOT NULL,
    -- local machine time
    timestamp INTEGER NOT NULL,
    data BLOB NOT NULL
);

CREATE INDEX IF NOT EXISTS event_log_timestamp_conversation_id_type ON event_log (timestamp, conversation_id, type);