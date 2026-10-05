package app.auth

import app.utils.ResourceId
import app.utils.toResourceId
import db.tables.daos.AppUserDao
import db.tables.pojos.AppUser
import db.tables.references.APP_USER
import org.jooq.DSLContext
import java.util.UUID

class UserRepository(
  private val dsl: DSLContext,
  private val dao: AppUserDao = AppUserDao(dsl.configuration()),
) {
  fun insert(user: AppUser) {
    dao.insert(user)
  }

  fun findByUsername(username: String): AppUser? =
    dsl
      .selectFrom(APP_USER)
      .where(APP_USER.USERNAME.eq(username))
      .fetchOneInto(AppUser::class.java)

  companion object {
    fun newUserId(): ResourceId =
      UUID
        .randomUUID()
        .toResourceId("usr")
  }
}
