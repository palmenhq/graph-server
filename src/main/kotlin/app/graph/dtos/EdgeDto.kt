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
  val fromNodeTitle: String,
  val toNode: ResourceId,
  val toNodeTitle: String,
)

fun Edge.toDto(
  edgeType: EdgeType,
  fromNodeTitle: String,
  toNodeTitle: String,
) = EdgeDto(
  id = id!!,
  title = title,
  relationshipTitle = edgeType.relationshipTitle!!,
  reverseRelationshipTitle = edgeType.reverseRelationshipTitle,
  fromNode = fromNode!!,
  fromNodeTitle = fromNodeTitle,
  toNode = toNode!!,
  toNodeTitle = toNodeTitle,
)

fun NodeEdgeDbo.toEdgeDto() =
  edge.toDto(
    edgeType = edgeType,
    fromNodeTitle = fromNode.title!!,
    toNodeTitle = toNode.title!!,
  )
