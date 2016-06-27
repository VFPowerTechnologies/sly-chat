ALTER TABLE conversation_info RENAME TO conversation_info_old;

CREATE TABLE IF NOT EXISTS conversation_info (
    contact_id INTEGER PRIMARY KEY NOT NULL,
    unread_count INTEGER NOT NULL,
    last_message TEXT,
    -- unix time, in milliseconds
    last_timestamp INTEGER,

    FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE
);

INSERT INTO
    conversation_info
    (contact_id, unread_count, last_message, last_timestamp)
SELECT
    contact_id, unread_count, last_message, last_timestamp
FROM
    conversation_info_old
;

DROP TABLE conversation_info_old;
