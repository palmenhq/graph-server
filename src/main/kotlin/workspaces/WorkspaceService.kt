package workspaces

import db.tables.pojos.Workspace
import utils.ResourceId

class WorkspaceService(private val workspaceRepository: WorkspaceRepository) {
  fun createWorkspace(name: String): Workspace {
    val workspace = Workspace().apply {
      this.id = WorkspaceRepository.newResourceId()
      this.name = name
    }
    workspaceRepository.insert(workspace)

    return workspace
  }

  fun getById(id: ResourceId): Workspace? = workspaceRepository.findById(id)
}
