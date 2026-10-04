package app.graph.validation

import app.utils.ResourceId
import app.utils.http.ClientException

class NoSuchNodeTypeException(
  nodeTypeId: ResourceId,
) : ClientException("node_type_not_found", "Node type $nodeTypeId not found")

class NoSuchEdgeTypeException(
  edgeTypeId: ResourceId,
) : ClientException("edge_type_not_found", "Edge type $edgeTypeId not found")

class NoSuchNodeException(
  nodeId: ResourceId,
) : ClientException("node_not_found", "Node $nodeId not found")

class NoSuchEdgeException(
  edgeId: ResourceId,
) : ClientException("edge_not_found", "Edge $edgeId not found")
