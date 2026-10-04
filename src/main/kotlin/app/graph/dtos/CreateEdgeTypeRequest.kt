package app.graph.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreateEdgeTypeRequest(
  val relationshipTitle: String,
  val reverseRelationshipTitle: String?,
)
