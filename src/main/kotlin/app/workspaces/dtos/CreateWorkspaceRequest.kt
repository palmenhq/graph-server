package app.workspaces.dtos

import kotlinx.serialization.Serializable

@Serializable
data class CreateWorkspaceRequest(
  val name: String,
)
