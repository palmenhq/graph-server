-- liquibase formatted sql
-- changeset palmenhq:012

ALTER TABLE note
    ADD COLUMN updated_at timestamptz NULL
;

-- rollback alter table note drop column updated_at;