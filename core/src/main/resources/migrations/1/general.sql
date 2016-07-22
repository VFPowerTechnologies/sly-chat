CREATE TABLE IF NOT EXISTS package_queue (
    user_id INTEGER NOT NULL,
    device_id INTEGER NOT NULL,
    message_id TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    payload TEXT NOT NULL,

    UNIQUE (user_id, message_id)
);
