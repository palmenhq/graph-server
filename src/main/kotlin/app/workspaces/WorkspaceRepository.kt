package app.workspaces

import app.utils.AppClock
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
        .set(WORKSPACE_MEMBER.CREATED_AT, AppClock.now())
        .execute()
    }
  }

  fun findById(id: ResourceId): Workspace? =
    dsl
      .selectFrom(WORKSPACE)
      .where(WORKSPACE.ID.eq(id))
      .and(WORKSPACE.DELETED_AT.isNull)
      .fetchOneInto(Workspace::class.java)

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
      .and(WORKSPACE.DELETED_AT.isNull)
      .fetchOneInto(Workspace::class.java)

  fun findAllForMember(userId: ResourceId): List<Workspace> =
    dsl
      .select(WORKSPACE.asterisk())
      .from(WORKSPACE)
      .join(WORKSPACE_MEMBER)
      .on(WORKSPACE_MEMBER.WORKSPACE_ID.eq(WORKSPACE.ID))
      .where(WORKSPACE_MEMBER.USER_ID.eq(userId))
      .and(WORKSPACE.DELETED_AT.isNull)
      .orderBy(WORKSPACE.CREATED_AT, WORKSPACE.ID)
      .fetchInto(Workspace::class.java)

  fun isMember(
    workspaceId: ResourceId,
    userId: ResourceId,
  ): Boolean =
    dsl.fetchExists(
      dsl
        .selectOne()
        .from(WORKSPACE_MEMBER)
        .join(WORKSPACE)
        .on(WORKSPACE.ID.eq(WORKSPACE_MEMBER.WORKSPACE_ID))
        .where(WORKSPACE_MEMBER.WORKSPACE_ID.eq(workspaceId))
        .and(WORKSPACE_MEMBER.USER_ID.eq(userId))
        .and(WORKSPACE.DELETED_AT.isNull),
    )

  /**
   * @return the number of updated rows, 0 if the workspace doesn't exist or is deleted
   */
  fun updateName(
    id: ResourceId,
    name: String,
  ): Int =
    dsl
      .update(WORKSPACE)
      .set(WORKSPACE.NAME, name)
      .where(WORKSPACE.ID.eq(id))
      .and(WORKSPACE.DELETED_AT.isNull)
      .execute()

  /**
   * @return the number of updated rows, 0 if the workspace doesn't exist or is already deleted
   */
  fun softDelete(
    id: ResourceId,
    deletedAt: OffsetDateTime,
  ): Int =
    dsl
      .update(WORKSPACE)
      .set(WORKSPACE.DELETED_AT, deletedAt)
      .where(WORKSPACE.ID.eq(id))
      .and(WORKSPACE.DELETED_AT.isNull)
      .execute()

  companion object {
    fun newResourceId(): ResourceId =
      UUID
        .randomUUID()
        .toResourceId("wskp")
  }
}
