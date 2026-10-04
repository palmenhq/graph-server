-- liquibase formatted sql
-- changeset palmenhq:005

CREATE TYPE property_value_type AS ENUM ('text', 'instagram', 'tiktok', 'phone', 'email');

CREATE TABLE node_property
(
    id           text PRIMARY KEY,
    node_type_id text                NOT NULL REFERENCES node_type (id),
    name         text                NOT NULL,
    value_type   property_value_type NOT NULL,
    created_at   timestamptz         NOT NULL,
    deleted_at   timestamptz,
    workspace_id text                NOT NULL REFERENCES workspace (id)
);

CREATE INDEX node_property_node_type_id_idx ON node_property (node_type_id);
CREATE INDEX node_property_workspace_id_idx ON node_property (workspace_id);
CREATE UNIQUE INDEX node_property_unique_name_idx ON node_property (node_type_id, name) WHERE deleted_at IS NULL;

CREATE TABLE node_property_value
(
    node_id      text                NOT NULL REFERENCES node (id),
    property_id  text                NOT NULL REFERENCES node_property (id),
    value_type   property_value_type NOT NULL,
    value        text,
    updated_at   timestamptz         NOT NULL,
    workspace_id text                NOT NULL REFERENCES workspace (id),
    PRIMARY KEY (node_id, property_id)
);

CREATE INDEX node_property_value_property_id_idx ON node_property_value (property_id);

-- rollback DROP TABLE node_property_value; DROP TABLE node_property; DROP TYPE property_value_type;
