-- Conversation table template; placeholder should be replaced at creation time
-- Make sure to escape any ` in the name when substituting.
CREATE TABLE IF NOT EXISTS `conv_%id%` (
    -- message uuid
    id TEXT PRIMARY KEY NOT NULL,
    -- NULL for your own messages
    speaker_contact_id INTEGER,
    -- unix time, in milliseconds
    -- this is set when you sent the message
    timestamp INTEGER NOT NULL,
    -- for sent messages, this is updated when the server receives the message
    -- for received message, this is set to the time the client received the message from the server
    received_timestamp INTEGER NOT NULL,
    -- used when timestamp is equal to guarantee order
    n INTEGER NOT NULL,
    is_read INTEGER NOT NULL,
    is_expired INTEGER NOT NULL,
    -- number of ms from view until the message is destroyed
    -- 0 indicates the message has no time limit
    ttl INTEGER NOT NULL,
    -- a time when this message should no longer be viewable
    -- 0 indicates no time limit
    expires_at INTEGER NOT NULL,
    -- if is_sent = true and this is 1, then the message was received by the relay server
    -- if is_sent is false this value should be set to 1
    is_delivered INTEGER NOT NULL,
    message TEXT NOT NULL,
    -- true if this message has associated failures in the message_failures table
    has_failures INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS `unique_conv_%id%_timestamp_n` ON `conv_%id%` (timestamp, n);
CREATE INDEX IF NOT EXISTS `idx_conv_%id%_is_read` ON `conv_%id%` (is_read);
