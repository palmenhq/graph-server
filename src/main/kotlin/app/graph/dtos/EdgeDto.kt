package app.graph.dtos

import app.graph.db.NodeEdgeDbo
import app.utils.ResourceId
import db.tables.pojos.Edge
import db.tables.pojos.EdgeType
import kotlinx.serialization.Serializable

@Serializable
data class EdgeDto(
  val id: ResourceId,
  val title: String?,
  val relationshipTitle: String,
  val reverseRelationshipTitle: String?,
  val fromNode: ResourceId,
  val toNode: ResourceId,
)

fun Edge.toDto(edgeType: EdgeType) =
  EdgeDto(
    id = id!!,
    title = title,
    relationshipTitle = edgeType.relationshipTitle!!,
    reverseRelationshipTitle = edgeType.reverseRelationshipTitle,
    fromNode = fromNode!!,
    toNode = toNode!!,
  )

fun NodeEdgeDbo.toEdgeDto() = edge.toDto(edgeType)
