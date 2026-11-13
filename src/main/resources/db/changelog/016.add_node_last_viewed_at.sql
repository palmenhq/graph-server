-- liquibase formatted sql
-- changeset palmenhq:016

ALTER TABLE node
    ADD COLUMN last_viewed_at timestamptz
;

UPDATE node
SET last_viewed_at = created_at
;

ALTER TABLE node
    ALTER COLUMN last_viewed_at SET NOT NULL
;

CREATE INDEX node_workspace_last_viewed_at_id_idx
    ON node (workspace_id, last_viewed_at DESC, id)
;

-- rollback drop index node_workspace_last_viewed_at_id_idx;
-- rollback alter table node drop column last_viewed_at;
