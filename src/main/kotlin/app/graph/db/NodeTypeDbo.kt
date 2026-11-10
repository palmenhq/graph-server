package app.graph.db

import app.utils.ResourceId
import db.tables.pojos.NodeType

data class NodeTypeDbo(
  val nodeType: NodeType,
  val preferredEdgeTypes: List<PreferredEdgeTypeDbo>,
) {
  val id: ResourceId get() = nodeType.id!!
  val title: String get() = nodeType.title!!
}

data class PreferredEdgeTypeDbo(
  val edgeTypeId: ResourceId,
  val reverse: Boolean,
)
