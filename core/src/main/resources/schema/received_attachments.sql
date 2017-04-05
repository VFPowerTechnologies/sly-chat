CREATE TABLE received_attachments (
    conversation_id TEXT NOT NULL,
    message_id TEXT NOT NULL,
    n INTEGER NOT NULL,

    their_file_id TEXT NOT NULL,
    their_share_key TEXT NOT NULL,
    -- duplicated for simplicity
    our_file_id TEXT NOT NULL,

    -- UserMetadata
    file_key TEXT NOT NULL,
    cipher_id INTEGER NOT NULL,
    directory TEXT NOT NULL,
    file_name TEXT NOT NULL,
    shared_from_user_id INTEGER NOT NULL,
    shared_from_group_id TEXT,

    FOREIGN KEY (conversation_id, message_id, n) REFERENCES attachments (conversation_id, message_id, n) ON DELETE CASCADE
);
