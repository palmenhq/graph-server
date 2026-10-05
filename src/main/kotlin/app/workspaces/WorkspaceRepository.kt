package app.workspaces

import app.utils.ResourceId
import app.utils.toResourceId
import db.tables.daos.WorkspaceDao
import db.tables.pojos.Workspace
import db.tables.references.WORKSPACE
import db.tables.references.WORKSPACE_MEMBER
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.util.UUID

class WorkspaceRepository(
  private val dsl: DSLContext,
  private val dao: WorkspaceDao = WorkspaceDao(dsl.configuration()),
) {
  /**
   * Inserts the workspace and makes [memberId] a member of it, atomically.
   */
  fun insert(
    workspace: Workspace,
    memberId: ResourceId,
  ) {
    dsl.transaction { config ->
      val tx = DSL.using(config)
      WorkspaceDao(config).insert(workspace)
      tx
        .insertInto(WORKSPACE_MEMBER)
        .set(WORKSPACE_MEMBER.WORKSPACE_ID, workspace.id)
        .set(WORKSPACE_MEMBER.USER_ID, memberId)
        .set(WORKSPACE_MEMBER.CREATED_AT, OffsetDateTime.now())
        .execute()
    }
  }

  fun findById(id: ResourceId): Workspace? = dao.findById(id)

  fun findByIdForMember(
    id: ResourceId,
    userId: ResourceId,
  ): Workspace? =
    dsl
      .select(WORKSPACE.asterisk())
      .from(WORKSPACE)
      .join(WORKSPACE_MEMBER)
      .on(WORKSPACE_MEMBER.WORKSPACE_ID.eq(WORKSPACE.ID))
      .where(WORKSPACE.ID.eq(id))
      .and(WORKSPACE_MEMBER.USER_ID.eq(userId))
      .fetchOneInto(Workspace::class.java)

  fun findAllForMember(userId: ResourceId): List<Workspace> =
    dsl
      .select(WORKSPACE.asterisk())
      .from(WORKSPACE)
      .join(WORKSPACE_MEMBER)
      .on(WORKSPACE_MEMBER.WORKSPACE_ID.eq(WORKSPACE.ID))
      .where(WORKSPACE_MEMBER.USER_ID.eq(userId))
      .orderBy(WORKSPACE.CREATED_AT, WORKSPACE.ID)
      .fetchInto(Workspace::class.java)

  fun isMember(
    workspaceId: ResourceId,
    userId: ResourceId,
  ): Boolean =
    dsl.fetchExists(
      WORKSPACE_MEMBER,
      WORKSPACE_MEMBER.WORKSPACE_ID.eq(workspaceId).and(WORKSPACE_MEMBER.USER_ID.eq(userId)),
    )

  companion object {
    fun newResourceId(): ResourceId =
      UUID
        .randomUUID()
        .toResourceId("wskp")
  }
}
