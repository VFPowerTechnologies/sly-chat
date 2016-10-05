-- +contacts.allowed_message_level
-- -contacts.is_pending
-- contacts.public_key[BLOB -> STRING]

ALTER TABLE contacts RENAME TO contacts_old;

CREATE TABLE IF NOT EXISTS contacts (
    id INTEGER PRIMARY KEY NOT NULL,
    email TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    allowed_message_level INTEGER NOT NULL,
    phone_number TEXT,
    public_key TEXT NOT NULL
);

INSERT INTO
    contacts
    (id, email, name, allowed_message_level, phone_number, public_key)
SELECT
    -- set to ALL message level
    id, email, name, 2, phone_number, public_key
FROM
    contacts_old
;

DROP TABLE contacts_old;

--  +send_message_queue
--  +groups
--  +group_members
--  +group_conversation_info

CREATE TABLE IF NOT EXISTS send_message_queue (
    user_id INTEGER NOT NULL,
    group_id STRING,
    category STRING NOT NULL,
    message_id STRING NOT NULL,
    timestamp INTEGER NOT NULL,
    serialized BLOB NOT NULL,

    PRIMARY KEY (user_id, message_id),
    FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS groups (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    membership_level INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS group_conversation_info (
    group_id TEXT PRIMARY KEY,
    last_speaker_contact_id INTEGER,
    unread_count INTEGER NOT NULL,
    last_message TEXT,
    last_timestamp INTEGER,

    FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    FOREIGN KEY (last_speaker_contact_id) REFERENCES contacts (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS group_members (
    group_id TEXT NOT NULL,
    contact_id INTEGER NOT NULL,

    FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE,

    UNIQUE (group_id, contact_id)
);
