CREATE TABLE IF NOT EXISTS message_failures (
    conversation_id TEXT NOT NULL,
    contact_id INTEGER NOT NULL,
    message_id TEXT PRIMARY KEY NOT NULL,
    reason TEXT NOT NULL,

    FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS `message_failures_unique_conversation_id_message_id_contact_id` ON `message_failures` (conversation_id, message_id, contact_id);
