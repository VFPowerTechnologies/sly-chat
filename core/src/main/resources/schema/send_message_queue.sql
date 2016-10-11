CREATE TABLE IF NOT EXISTS send_message_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    contact_id INTEGER NOT NULL,
    group_id STRING,
    category STRING NOT NULL,
    message_id STRING NOT NULL,
    serialized BLOB NOT NULL,

    UNIQUE (contact_id, message_id),
    FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE,
    FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE
)