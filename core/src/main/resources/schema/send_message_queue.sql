CREATE TABLE IF NOT EXISTS send_message_queue (
    user_id INTEGER NOT NULL,
    -- TODO fk
    group_id STRING,
    category STRING NOT NULL,
    message_id STRING NOT NULL,
    timestamp INTEGER NOT NULL,
    serialized BLOB NOT NULL,

    PRIMARY KEY (user_id, message_id)
)