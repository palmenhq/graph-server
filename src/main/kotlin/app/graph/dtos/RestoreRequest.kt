package app.graph.dtos

import app.utils.ResourceId
import kotlinx.serialization.Serializable

@Serializable
data class RestoreRequest(
  val id: ResourceId,
)
