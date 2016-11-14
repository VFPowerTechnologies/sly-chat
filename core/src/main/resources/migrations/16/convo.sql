ALTER TABLE %tableName% RENAME TO convo_old;

CREATE TABLE IF NOT EXISTS `%tableName%` (
    id TEXT PRIMARY KEY NOT NULL,
    speaker_contact_id INTEGER,
    timestamp INTEGER NOT NULL,
    received_timestamp INTEGER NOT NULL,
    n INTEGER NOT NULL,
    is_read INTEGER NOT NULL,
    is_expired INTEGER NOT NULL,
    ttl INTEGER NOT NULL,
    expires_at INTEGER NOT NULL,
    is_delivered INTEGER NOT NULL,
    message TEXT NOT NULL,
    has_failures INTEGER NOT NULL
);

INSERT INTO
    %tableName%
    (id, speaker_contact_id, timestamp, received_timestamp, n, is_read, is_expired, ttl, expires_at, is_delivered, message, has_failures)
SELECT
   id, speaker_contact_id, timestamp, received_timestamp, n, is_read, is_expired, ttl, expires_at, is_delivered, message, 0
FROM
    convo_old
;

CREATE UNIQUE INDEX IF NOT EXISTS `unique_%tableName%_timestamp_n` ON `%tableName%` (timestamp, n);
CREATE INDEX IF NOT EXISTS `idx_%tableName%_is_read` ON `%tableName%` (is_read);

DROP TABLE convo_old;
