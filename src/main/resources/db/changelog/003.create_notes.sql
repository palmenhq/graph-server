-- liquibase formatted sql
-- changeset palmenhq:003

CREATE TABLE note
(
    id           text PRIMARY KEY,
    body         jsonb       NOT NULL,
    created_at   timestamptz NOT NULL,
    workspace_id text        NOT NULL REFERENCES workspace (id)
);

CREATE INDEX note_workspace_id_created_at_idx ON note (workspace_id, created_at);

CREATE TABLE note_mention
(
    id   text PRIMARY KEY,
    note text REFERENCES note (id),
    node text REFERENCES node (id)
);

CREATE INDEX note_mention_note_idx ON note_mention (note);
