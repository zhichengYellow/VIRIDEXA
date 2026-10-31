-- Control records only; not scientific observations or review events.
CREATE TABLE tidb_release_import_state (
 id INT NOT NULL PRIMARY KEY,
 manifest_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 CHECK(id=1)
);
CREATE TABLE tidb_release_import_progress (
 sequence_id INT NOT NULL PRIMARY KEY,
 statement_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 committed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
