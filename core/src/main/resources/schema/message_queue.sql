CREATE TABLE IF NOT EXISTS message_queue (
    user_id INTEGER NOT NULL,
    device_id INTEGER NOT NULL,
    message_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    payload TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS `unique_message_queue_user_id_message_id` ON `message_queue` (user_id, message_id);
