-- +send_message_queue.[user_id -> contact_id]
-- +send_message_queue[contact_id references contacts (id)]
-- -send_message_queue.timestamp
-- +send_message_queue.id
-- +send_message_queue[FOREIGN KEY (contact_id, message_id) -> UNIQUE]

ALTER TABLE send_message_queue RENAME TO send_message_queue_old;

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
);

INSERT INTO
    send_message_queue
    (contact_id, group_id, category, message_id, serialized)
SELECT
    user_id, group_id, category, message_id, serialized
FROM
    send_message_queue_old
ORDER BY
    timestamp;

DROP TABLE send_message_queue_old;
