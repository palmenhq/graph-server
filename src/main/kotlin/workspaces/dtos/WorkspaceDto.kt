package workspaces.dtos

import db.tables.pojos.Workspace
import io.ktor.openapi.JsonSchema
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

@Serializable
@JsonSchema.Title("Workspace")
data class WorkspaceDto(
  val id: String,
  val name: String,
  val createdAt: Instant,
)

fun Workspace.toDto() =
  WorkspaceDto(
    id = id!!,
    name = name!!,
    createdAt = createdAt!!.toInstant().toKotlinInstant(),
  )
