package app.graph.dtos

import app.utils.ResourceId
import kotlinx.serialization.Serializable

@Serializable
data class DeleteRequest(
  val id: ResourceId,
)
