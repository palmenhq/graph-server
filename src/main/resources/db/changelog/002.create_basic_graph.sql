-- liquibase formatted sql
-- changeset palmenhq:002

CREATE TABLE node_type
(
    id           text PRIMARY KEY,
    name         text        NOT NULL,
    created_at   timestamptz NOT NULL,
    workspace_id text REFERENCES workspace (id)
);

CREATE INDEX node_type_workspace_id_idx ON node_type (workspace_id);

CREATE TABLE node
(
    id           text PRIMARY KEY,
    type         text        NOT NULL REFERENCES node_type (id),
    title        text,
    workspace_id text REFERENCES workspace (id),
    created_at   timestamptz NOT NULL
);

CREATE INDEX node_create_at_idx ON node (created_at);
CREATE INDEX node_type_idx ON node (type, id);
CREATE INDEX node_workspace_id_idx ON node (workspace_id);

CREATE TABLE edge_type
(
    id                        text PRIMARY KEY,
    relationship_name         text        NOT NULL,
    reverse_relationship_name text,
    created_at                timestamptz NOT NULL,
    source_node_type          text REFERENCES node_type (id),
    target_node_type          text REFERENCES node_type (id),
    workspace_id              text REFERENCES workspace (id)
);

CREATE UNIQUE INDEX edge_type_unique_idx ON edge_type (source_node_type, target_node_type);
CREATE INDEX edge_type_workspace_id_ixd ON edge_type (workspace_id);

CREATE TABLE edge
(
    id           text PRIMARY KEY,
    type         text                      NOT NULL,
    from_node    text REFERENCES node (id) NOT NULL,
    to_node      text REFERENCES node (id) NOT NULL,
    title        text,
    created_at   timestamptz               NOT NULL,
    workspace_id text REFERENCES workspace (id)
);

CREATE INDEX edge_type_idx ON edge (type);
CREATE INDEX edge_from_node_idx ON edge (from_node);
CREATE INDEX edge_to_node_idx ON edge (to_node);
CREATE UNIQUE INDEX edge_unique_edges_idx ON edge (type, from_node, to_node);
CREATE INDEX edge_workspace_id ON edge (workspace_id);

-- rollback drop table edge; drop table edge_type; drop table node; drop table node_type;
