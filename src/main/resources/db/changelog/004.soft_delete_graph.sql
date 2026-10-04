-- liquibase formatted sql
-- changeset palmenhq:004

ALTER TABLE node_type ADD COLUMN deleted_at timestamptz;
ALTER TABLE node ADD COLUMN deleted_at timestamptz;
ALTER TABLE edge_type ADD COLUMN deleted_at timestamptz;
ALTER TABLE edge ADD COLUMN deleted_at timestamptz;

DROP INDEX edge_unique_edges_idx;
CREATE UNIQUE INDEX edge_unique_edges_idx ON edge (type, from_node, to_node) WHERE deleted_at IS NULL;

-- rollback DROP INDEX edge_unique_edges_idx; CREATE UNIQUE INDEX edge_unique_edges_idx ON edge (type, from_node, to_node); ALTER TABLE edge DROP COLUMN deleted_at; ALTER TABLE edge_type DROP COLUMN deleted_at; ALTER TABLE node DROP COLUMN deleted_at; ALTER TABLE node_type DROP COLUMN deleted_at;
