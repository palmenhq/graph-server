package app.graph.dtos

import app.utils.ResourceId
import db.tables.pojos.EdgeType
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

@Serializable
data class EdgeTypeDto(
  val id: ResourceId,
  val relationshipTitle: String,
  val reverseRelationshipTitle: String?,
  val createdAt: Instant,
  val workspaceId: ResourceId,
)

fun EdgeType.toDto(): EdgeTypeDto =
  EdgeTypeDto(
    id = id!!,
    relationshipTitle = relationshipTitle!!,
    reverseRelationshipTitle = reverseRelationshipTitle,
    createdAt = createdAt!!.toInstant().toKotlinInstant(),
    workspaceId = workspaceId!!,
  )
