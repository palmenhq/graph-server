-- liquibase formatted sql
-- changeset palmenhq:006

CREATE TABLE app_user
(
    id            text PRIMARY KEY,
    username      text        not null UNIQUE,
    password_hash text        not null,
    created_at    timestamptz not null
);

CREATE TABLE session
(
    id         text PRIMARY KEY, -- sha256 hash of the session token, never the token itself
    data       text        not null,
    created_at timestamptz not null,
    expires_at timestamptz not null
);

CREATE INDEX session_expires_at_idx ON session (expires_at);

-- rollback drop table session;
-- rollback drop table app_user;
