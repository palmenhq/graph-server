package app.workspaces

import app.utils.ResourceId
import app.workspaces.validation.NoSuchWorkspaceException
import db.tables.pojos.Workspace
import java.time.OffsetDateTime

class WorkspaceService(
  private val workspaceRepository: WorkspaceRepository,
) {
  fun createWorkspace(
    name: String,
    ownerId: ResourceId,
  ): Workspace {
    val workspace =
      Workspace().apply {
        this.id = WorkspaceRepository.newResourceId()
        this.name = name
        this.createdAt = OffsetDateTime.now()
      }
    workspaceRepository.insert(workspace, memberId = ownerId)

    return workspace
  }

  /**
   * Not access controlled. Use [getByIdForUser] when acting on behalf of a user.
   */
  fun getById(id: ResourceId): Workspace? = workspaceRepository.findById(id)

  fun getByIdForUser(
    id: ResourceId,
    userId: ResourceId,
  ): Workspace? = workspaceRepository.findByIdForMember(id = id, userId = userId)

  fun getAllForUser(userId: ResourceId): List<Workspace> = workspaceRepository.findAllForMember(userId)

  fun hasAccess(
    workspaceId: ResourceId,
    userId: ResourceId,
  ): Boolean = workspaceRepository.isMember(workspaceId = workspaceId, userId = userId)

  fun renameWorkspace(
    id: ResourceId,
    name: String,
    userId: ResourceId,
  ): Workspace {
    if (!hasAccess(workspaceId = id, userId = userId)) {
      throw NoSuchWorkspaceException()
    }
    if (workspaceRepository.updateName(id = id, name = name) == 0) {
      throw NoSuchWorkspaceException()
    }

    return workspaceRepository.findById(id) ?: throw NoSuchWorkspaceException()
  }

  fun deleteWorkspace(
    id: ResourceId,
    userId: ResourceId,
  ) {
    if (!hasAccess(workspaceId = id, userId = userId)) {
      throw NoSuchWorkspaceException()
    }
    if (workspaceRepository.softDelete(id = id, deletedAt = OffsetDateTime.now()) == 0) {
      throw NoSuchWorkspaceException()
    }
  }
}
