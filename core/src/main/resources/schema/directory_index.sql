CREATE TABLE directory_index (
    path TEXT NOT NULL,
    -- only the actual dir name; eg: path=/; subdir=images, even if images/profiles exists
    sub_dir TEXT NOT NULL,
    ref_count INTEGER NOT NULL
);

CREATE UNIQUE INDEX directory_index_uniq_path_sub_dir ON directory_index (path, sub_dir);
