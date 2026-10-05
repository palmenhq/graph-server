-- liquibase formatted sql
-- changeset palmenhq:007

-- Many-to-many between users and workspaces. Membership grants access to a workspace.
CREATE TABLE workspace_member
(
    workspace_id text        not null REFERENCES workspace (id) ON DELETE CASCADE,
    user_id      text        not null REFERENCES app_user (id) ON DELETE CASCADE,
    created_at   timestamptz not null,
    PRIMARY KEY (workspace_id, user_id)
);

CREATE INDEX workspace_member_user_id_idx ON workspace_member (user_id);

-- rollback drop table workspace_member;
