package app.workspaces.dtos

import kotlinx.serialization.Serializable

@Serializable
data class DeleteWorkspaceRequest(
  val id: String,
)
