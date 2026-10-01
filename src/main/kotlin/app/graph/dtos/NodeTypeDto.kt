package app.graph.dtos

import app.utils.ResourceId
import db.tables.pojos.NodeType
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

@Serializable
data class NodeTypeDto(
  val id: ResourceId,
  val name: String,
  val createdAt: Instant,
  val workspaceId: ResourceId,
)

fun NodeType.toDto() =
  NodeTypeDto(
    id = id!!,
    name = name!!,
    createdAt = createdAt!!.toInstant().toKotlinInstant(),
    workspaceId = workspaceId!!,
  )
