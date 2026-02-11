package workspaces

import db.tables.daos.WorkspaceDao
import db.tables.pojos.Workspace
import org.jooq.DSLContext
import utils.ResourceId
import utils.toResourceId
import java.util.UUID

class WorkspaceRepository(
  private val dsl: DSLContext,
  private val dao: WorkspaceDao = WorkspaceDao(dsl.configuration()),
) {
  fun insert(workspace: Workspace) {
    dao.insert(workspace)
  }

  fun findById(id: ResourceId): Workspace? = dao.findById(id)

  companion object {
    fun newResourceId(): ResourceId =
      UUID.randomUUID()
        .toResourceId("wskp")
  }
}
