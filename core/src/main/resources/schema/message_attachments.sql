CREATE TABLE message_attachments (
    conversation_id TEXT NOT NULL,
    message_id TEXT NOT NULL,
    attachment_id INTEGER NOT NULL,

    FOREIGN KEY (attachment_id) REFERENCES attachments (id) ON DELETE CASCADE
);

CREATE INDEX message_attachments_conversation_id_message_id ON message_attachments (conversation_id, message_id);
