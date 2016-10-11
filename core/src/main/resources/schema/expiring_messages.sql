CREATE TABLE IF NOT EXISTS expiring_messages (
    conversation_id TEXT NOT NULL,
    message_id TEXT NOT NULL,
    expires_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS `expiring_messages_unique_conversation_id_message_id` ON `expiring_messages` (conversation_id, message_id);
