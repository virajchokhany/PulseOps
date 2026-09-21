-- Pull request metadata, joined to deployments through commit_sha. This is how
-- the AI worker gets from "version 1.4.2 shipped" to "PR #482 changed the
-- payment provider timeout".

CREATE TABLE pull_requests (
    id          BIGSERIAL    PRIMARY KEY,
    number      INTEGER      NOT NULL,
    repository  VARCHAR(150) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    author      VARCHAR(150),
    commit_sha  VARCHAR(64)  NOT NULL,
    merged_at   TIMESTAMPTZ,
    url         VARCHAR(255),
    CONSTRAINT uq_pull_request UNIQUE (repository, number)
);

CREATE INDEX idx_pull_requests_commit ON pull_requests (commit_sha);

-- Changed files drive selective source retrieval: only files touched by a
-- suspect PR are read from Git and sent to the LLM.
CREATE TABLE pull_request_files (
    id              BIGSERIAL    PRIMARY KEY,
    pull_request_id BIGINT       NOT NULL REFERENCES pull_requests (id) ON DELETE CASCADE,
    file_path       VARCHAR(500) NOT NULL,
    change_type     VARCHAR(20)  NOT NULL,
    additions       INTEGER      NOT NULL DEFAULT 0,
    deletions       INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX idx_pull_request_files_pr ON pull_request_files (pull_request_id);
