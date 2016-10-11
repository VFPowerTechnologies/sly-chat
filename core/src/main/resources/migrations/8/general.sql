-- -remote_contact_updates.type
-- +remote_contact_updates.allowed_message_level
ALTER TABLE remote_contact_updates RENAME TO remote_contact_updates_old;

CREATE TABLE IF NOT EXISTS remote_contact_updates (
    contact_id INTEGER PRIMARY KEY NOT NULL,
    allowed_message_level INTEGER NOT NULL,
    FOREIGN KEY (contact_id) REFERENCES contacts (id) ON DELETE CASCADE
);

-- for the remove case, in the old set up the contact won't exist anymore; but since foreign_keys=off right now we can
-- get away with this since the condition'll never be checked.
INSERT INTO
   remote_contact_updates
   (contact_id, allowed_message_level)
SELECT
    contact_id,
    CASE type WHEN 'ADD' THEN 2
              WHEN 'REMOVE' THEN 1
              ELSE 2
    END
FROM
    remote_contact_updates_old
;
