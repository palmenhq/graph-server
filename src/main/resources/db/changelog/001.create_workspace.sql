-- liquibase formatted sql
-- changeset palmenhq:001

CREATE TABLE workspace
(
    id         text PRIMARY KEY,
    name       text,
    created_at timestamptz not null
);

-- rollback drop table workspace;
