-- liquibase formatted sql
-- changeset palmenhq:000

CREATE TABLE workspace (
    id text PRIMARY KEY,
    name text
);

-- rollback drop table workspace;
