package app.workspaces

import app.utils.ResourceId
import db.tables.pojos.Workspace
import java.time.OffsetDateTime

class WorkspaceService(
  private val workspaceRepository: WorkspaceRepository,
) {
  fun createWorkspace(name: String): Workspace {
    val workspace =
      Workspace().apply {
        this.id = WorkspaceRepository.newResourceId()
        this.name = name
        this.createdAt = OffsetDateTime.now()
      }
    workspaceRepository.insert(workspace)

    return workspace
  }

  fun getById(id: ResourceId): Workspace? = workspaceRepository.findById(id)

  fun getAll(): List<Workspace> = workspaceRepository.findAll()
}
