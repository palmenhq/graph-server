-- liquibase formatted sql
-- changeset palmenhq:011

CREATE TABLE node_type_preferred_edge_type
(
    node_type_id text    NOT NULL REFERENCES node_type (id),
    edge_type_id text    NOT NULL REFERENCES edge_type (id),
    reverse      boolean NOT NULL,
    position     integer NOT NULL,
    workspace_id text    NOT NULL REFERENCES workspace (id),
    PRIMARY KEY (node_type_id, edge_type_id, reverse)
);

CREATE INDEX node_type_preferred_edge_type_edge_type_id_idx ON node_type_preferred_edge_type (edge_type_id);

-- rollback drop table node_type_preferred_edge_type;
