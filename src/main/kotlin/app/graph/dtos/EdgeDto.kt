package app.graph.dtos

import app.utils.ResourceId
import db.tables.pojos.Edge
import kotlinx.serialization.Serializable

@Serializable
data class EdgeDto(
  val id: ResourceId,
  val fromNode: ResourceId,
  val toNode: ResourceId,
)

fun Edge.toDto() =
  EdgeDto(
    id = id!!,
    fromNode = fromNode!!,
    toNode = toNode!!,
  )
