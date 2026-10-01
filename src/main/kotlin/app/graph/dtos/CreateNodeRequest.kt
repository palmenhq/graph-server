package app.graph.dtos

import app.utils.ResourceId
import kotlinx.serialization.Serializable

@Serializable
data class CreateNodeRequest(
  val title: String,
  val nodeTypeId: ResourceId,
)
