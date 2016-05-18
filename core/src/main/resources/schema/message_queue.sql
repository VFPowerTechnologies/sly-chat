CREATE TABLE IF NOT EXISTS message_queue (
    address TEXT NOT NULL,
    message_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    message TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS `unique_message_queue_address_message_id` ON `message_queue` (address, message_id);
