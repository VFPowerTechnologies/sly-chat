ALTER TABLE `%tableName%` ADD COLUMN received_timestamp INTEGER NOT NULL DEFAULT 0;
UPDATE `%tableName%` SET received_timestamp = TIMESTAMP;