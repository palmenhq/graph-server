package app.graph.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreateNodeTypeRequest(
  val name: String,
)
