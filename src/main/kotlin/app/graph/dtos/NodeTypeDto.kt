package app.graph.dtos

import app.graph.db.NodeTypeDbo
import app.utils.ResourceId
import db.tables.pojos.NodeType
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

@Serializable
data class NodeTypeDto(
  val id: ResourceId,
  val title: String,
  val createdAt: Instant,
  val workspaceId: ResourceId,
  val preferredEdgeTypes: List<PreferredEdgeTypeDto>,
)

fun NodeType.toDto(): NodeTypeDto = toDto(emptyList())

fun NodeTypeDbo.toDto(): NodeTypeDto = nodeType.toDto(preferredEdgeTypes.map { it.toDto() })

private fun NodeType.toDto(preferredEdgeTypes: List<PreferredEdgeTypeDto>) =
  NodeTypeDto(
    id = id!!,
    title = title!!,
    createdAt = createdAt!!.toInstant().toKotlinInstant(),
    workspaceId = workspaceId!!,
    preferredEdgeTypes = preferredEdgeTypes,
  )
