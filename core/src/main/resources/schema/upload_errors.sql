CREATE TABLE IF NOT EXISTS upload_errors (
    upload_id TEXT UNIQUE NOT NULL,
    -- ???
    error TEXT NOT NULL,
    -- if we can retry later? things like quota should be mapped as fatal since they require user intervention
    fatal BOOLEAN NOT NULL,
    FOREIGN KEY (upload_id) REFERENCES uploads (id) ON DELETE CASCADE
);