-- Conversation table template; placeholder should be replaced at creation time
-- Make sure to escape any ` in the name when substituting.
CREATE TABLE IF NOT EXISTS `conv_%name%` (
    -- message uuid
    id TEXT PRIMARY KEY NOT NULL,
    -- whether we sent this message or received it
    is_sent INTEGER NOT NULL,
    -- unix time, in milliseconds
    -- for sent messages, this is set twice; once on insertion, and then updated when setting is_delivered=1
    -- this is to allow proper ordering of unsent messages
    timestamp INTEGER NOT NULL,
    -- used when timestamp is equal to guarantee order
    n INTEGER NOT NULL,
    -- for messages that expire, this should be set to a unix time (in ms) in the future; otherwise 0
    ttl INTEGER NOT NULL,
    -- if is_sent = true and this is 1, then the message was received by the relay server
    -- if is_sent is false this value should be set to 1
    is_delivered INTEGER NOT NULL,
    is_read INTEGER NOT NULL,
    message TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS `unique_conv_%name%_timestamp_n` ON `conv_%name%` (timestamp, n);
CREATE INDEX IF NOT EXISTS `idx_conv_%name%_is_read` ON `conv_%name%` (is_read);