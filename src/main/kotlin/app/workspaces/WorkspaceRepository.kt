package app.workspaces

import app.utils.ResourceId
import app.utils.toResourceId
import db.tables.daos.WorkspaceDao
import db.tables.pojos.Workspace
import org.jooq.DSLContext
import java.util.UUID

class WorkspaceRepository(
  private val dsl: DSLContext,
  private val dao: WorkspaceDao = WorkspaceDao(dsl.configuration()),
) {
  fun insert(workspace: Workspace) {
    dao.insert(workspace)
  }

  fun findById(id: ResourceId): Workspace? = dao.findById(id)

  fun findAll(): List<Workspace> = dao.findAll()

  companion object {
    fun newResourceId(): ResourceId =
      UUID
        .randomUUID()
        .toResourceId("wskp")
  }
}
