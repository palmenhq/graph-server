package app.graph.dtos

import app.utils.ResourceId
import db.tables.pojos.EdgeType
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

data class EdgeTypeDto(
  val id: ResourceId,
  val relationshipName: String,
  val reverseRelationshipName: String?,
  val createdAt: Instant,
  val sourceNodeType: ResourceId,
  val targetNodeType: ResourceId,
  val workspaceId: ResourceId,
)

fun EdgeType.toDto(): EdgeTypeDto =
  EdgeTypeDto(
    id = id!!,
    relationshipName = relationshipName!!,
    reverseRelationshipName = reverseRelationshipName,
    createdAt = createdAt!!.toInstant().toKotlinInstant(),
    sourceNodeType = sourceNodeType!!,
    targetNodeType = targetNodeType!!,
    workspaceId = workspaceId!!,
  )
