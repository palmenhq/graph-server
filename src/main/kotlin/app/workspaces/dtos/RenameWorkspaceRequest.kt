package app.workspaces.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RenameWorkspaceRequest(
  val id: String,
  val name: String,
)
