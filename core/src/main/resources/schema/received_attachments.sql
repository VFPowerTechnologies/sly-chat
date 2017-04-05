CREATE TABLE received_attachments (
    conversation_id TEXT NOT NULL,
    message_id TEXT NOT NULL,
    n INTEGER NOT NULL,

    their_file_id TEXT NOT NULL,
    their_share_key TEXT NOT NULL,
    file_key TEXT NOT NULL,

    FOREIGN KEY (conversation_id, message_id, n) REFERENCES attachments (conversation_id, message_id, n) ON DELETE CASCADE
);
