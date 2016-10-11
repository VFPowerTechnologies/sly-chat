-- Single row table
-- Key ids are capped to the value of org.whispersystems.libaxolotl.util.Medium.MAX_VALUE-1
CREATE TABLE IF NOT EXISTS prekey_ids (
    next_signed_id INTEGER NOT NULL CONSTRAINT next_signed_id_validity CHECK (next_signed_id >= 1 AND next_signed_id < 0xffffff),
    next_unsigned_id INTEGER NOT NULL CONSTRAINT next_unsigned_id_validity CHECK (next_unsigned_id >= 1 AND next_unsigned_id < 0xffffff)
)
