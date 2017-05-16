CREATE TABLE IF NOT EXISTS attachments (
    conversation_id TEXT NOT NULL,

    message_id TEXT NOT NULL,

    n INTEGER NOT NULL,

    -- name given by the sender (for display purposes in message logs)
    display_name TEXT NOT NULL,

    -- may be no longer be valid, so we can't use a foreign key here
    file_id TEXT NOT NULL,

    PRIMARY KEY (conversation_id, message_id, n)
);

CREATE INDEX attachments_idx_file_id ON attachments (file_id);
