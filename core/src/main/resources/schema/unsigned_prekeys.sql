-- Unsigned PreKeys
CREATE TABLE IF NOT EXISTS unsigned_prekeys (
    id INTEGER PRIMARY KEY CONSTRAINT id_validity CHECK (id >= 1 AND id <= 0xffffff),
    serialized BLOB NOT NULL
)