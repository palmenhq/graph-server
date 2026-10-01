package app.graph.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreateEdgeTypeRequest(
  val sourceNodeType: String,
  val targetNodeType: String,
  val relationshipName: String,
  val reverseRelationshipName: String?,
)
