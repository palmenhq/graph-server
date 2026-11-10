-- liquibase formatted sql
-- changeset palmenhq:010

ALTER TABLE note
    ADD COLUMN node_id text NULL REFERENCES node (id)
;

CREATE INDEX note_node_id_idx ON note (node_id);
CREATE INDEX note_mention_node_idx ON note_mention (node);

-- rollback drop index note_mention_node_idx;
-- rollback drop index note_node_id_idx;
-- rollback alter table note drop column node_id;
