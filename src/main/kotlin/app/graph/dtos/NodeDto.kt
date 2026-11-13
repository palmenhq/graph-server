package app.graph.dtos

import app.graph.db.NodeEdgeDbo
import app.utils.ResourceId
import db.tables.pojos.Node
import db.tables.pojos.NodeProperty
import db.tables.pojos.NodePropertyValue
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

sealed interface NodeDto {
  val id: ResourceId
  val title: String
  val nodeTypeId: ResourceId
  val createdAt: Instant
  val lastViewedAt: Instant

  val deletedAt: Instant?
}

@Serializable
data class FullNodeDto(
  override val id: ResourceId,
  override val title: String,
  override val nodeTypeId: ResourceId,
  override val createdAt: Instant,
  override val lastViewedAt: Instant,
  override val deletedAt: Instant?,
  val edges: List<EdgeDto>,
  val neighbors: List<NeighborNodeDto>,
  val properties: List<PropertyValueDto>,
) : NodeDto

@Serializable
data class NeighborNodeDto(
  override val id: ResourceId,
  override val title: String,
  override val nodeTypeId: ResourceId,
  override val createdAt: Instant,
  override val lastViewedAt: Instant,
  override val deletedAt: Instant?,
) : NodeDto

fun Node.toFullDto(
  edges: List<NodeEdgeDbo>,
  neighbors: List<Node>,
  properties: List<Pair<NodeProperty, NodePropertyValue>>,
): FullNodeDto =
  FullNodeDto(
    id = id!!,
    title = title!!,
    nodeTypeId = type!!,
    createdAt = createdAt!!.toInstant().toKotlinInstant(),
    lastViewedAt = lastViewedAt!!.toInstant().toKotlinInstant(),
    deletedAt = deletedAt?.toInstant()?.toKotlinInstant(),
    edges = edges.map { it.toEdgeDto() },
    neighbors = neighbors.map { it.toNeighborDto() },
    properties = properties.map { it.second.toDto(it.first.name!!) },
  )

fun Node.toNeighborDto(): NeighborNodeDto =
  NeighborNodeDto(
    id = id!!,
    title = title!!,
    nodeTypeId = type!!,
    createdAt = createdAt!!.toInstant().toKotlinInstant(),
    lastViewedAt = lastViewedAt!!.toInstant().toKotlinInstant(),
    deletedAt = deletedAt?.toInstant()?.toKotlinInstant(),
  )
