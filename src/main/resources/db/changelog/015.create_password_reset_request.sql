-- liquibase formatted sql
-- changeset palmenhq:015

CREATE TABLE password_reset_request
(
    id         text PRIMARY KEY,
    user_id    text        not null REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash text        not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at    timestamptz
);

CREATE INDEX password_reset_request_user_id_idx ON password_reset_request (user_id);

-- rollback drop table password_reset_request;