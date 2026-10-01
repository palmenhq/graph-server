package app.graph.dtos

import app.utils.ResourceId
import kotlinx.serialization.Serializable

@Serializable
data class CreateEdgeRequest(
  val edgeTypeId: ResourceId,
  val fromNode: ResourceId,
  val toNode: ResourceId,
  val title: String? = null,
)
