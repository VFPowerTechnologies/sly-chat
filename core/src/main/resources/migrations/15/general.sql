CREATE TABLE IF NOT EXISTS event_log (
    conversation_id TEXT,
    type TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    data BLOB NOT NULL
);

CREATE INDEX IF NOT EXISTS event_log_timestamp_conversation_id_type ON event_log (timestamp, conversation_id, type);
