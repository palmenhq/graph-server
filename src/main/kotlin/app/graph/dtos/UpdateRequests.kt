package app.graph.dtos

import app.utils.ResourceId
import kotlinx.serialization.Serializable

@Serializable
data class UpdateNodeTypeRequest(
  val id: ResourceId,
  val title: String,
)

@Serializable
data class UpdateEdgeTypeRequest(
  val id: ResourceId,
  val relationshipTitle: String,
  val reverseRelationshipTitle: String? = null,
)

@Serializable
data class UpdateNodeRequest(
  val id: ResourceId,
  val title: String,
)

@Serializable
data class UpdateEdgeRequest(
  val id: ResourceId,
  val title: String? = null,
)
