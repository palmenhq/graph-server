-- liquibase formatted sql
-- changeset palmenhq:014

ALTER TABLE node
    ADD COLUMN search_vector tsvector NOT NULL DEFAULT ''::tsvector
;

CREATE INDEX node_search_vector_idx ON node USING GIN (search_vector);

-- rollback drop index node_search_vector_idx;
-- rollback alter table node drop column search_vector;
