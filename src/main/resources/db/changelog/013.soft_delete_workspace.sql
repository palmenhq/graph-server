-- liquibase formatted sql
-- changeset palmenhq:013

ALTER TABLE workspace
    ADD COLUMN deleted_at timestamptz NULL
;

-- rollback alter table workspace drop column deleted_at;
