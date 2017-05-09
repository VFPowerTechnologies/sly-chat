-- This table tracks refs to file ids from the attachment tables
-- Used so we can purge the cache once the ref count drops to zero
CREATE TABLE attachment_cache_refcounts (
    file_id TEXT NOT NULL PRIMARY KEY,
    ref_count INTEGER NOT NULL
);