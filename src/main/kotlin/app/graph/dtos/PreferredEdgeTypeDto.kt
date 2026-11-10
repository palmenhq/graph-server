package app.graph.dtos

import app.graph.db.PreferredEdgeTypeDbo
import kotlinx.serialization.Serializable

@Serializable
data class PreferredEdgeTypeDto(
  val edgeTypeId: String,
  val reverse: Boolean,
)

fun PreferredEdgeTypeDbo.toDto() = PreferredEdgeTypeDto(edgeTypeId = edgeTypeId, reverse = reverse)

fun PreferredEdgeTypeDto.toDbo() = PreferredEdgeTypeDbo(edgeTypeId = edgeTypeId, reverse = reverse)
