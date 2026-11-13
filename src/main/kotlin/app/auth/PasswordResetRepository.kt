package app.auth

import db.tables.daos.PasswordResetRequestDao
import db.tables.pojos.PasswordResetRequest
import db.tables.references.PASSWORD_RESET_REQUEST
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.OffsetDateTime

class PasswordResetRepository(
  private val dsl: DSLContext,
) {
  fun <T> transaction(block: (DSLContext) -> T): T =
    dsl.transactionResult { configuration ->
      block(DSL.using(configuration))
    }

  fun insert(request: PasswordResetRequest) {
    PasswordResetRequestDao(dsl.configuration())
      .insert(request)
  }

  fun findById(id: String): PasswordResetRequest? =
    dsl
      .selectFrom(PASSWORD_RESET_REQUEST)
      .where(PASSWORD_RESET_REQUEST.ID.eq(id))
      .fetchOneInto(PasswordResetRequest::class.java)

  fun invalidateOpenForUser(
    userId: String,
    now: OffsetDateTime,
  ): Int =
    dsl
      .update(PASSWORD_RESET_REQUEST)
      .set(PASSWORD_RESET_REQUEST.USED_AT, now)
      .where(PASSWORD_RESET_REQUEST.USER_ID.eq(userId))
      .and(PASSWORD_RESET_REQUEST.USED_AT.isNull)
      .and(PASSWORD_RESET_REQUEST.EXPIRES_AT.gt(now))
      .execute()

  fun markUsed(
    id: String,
    now: OffsetDateTime,
  ): Int =
    dsl
      .update(PASSWORD_RESET_REQUEST)
      .set(PASSWORD_RESET_REQUEST.USED_AT, now)
      .where(PASSWORD_RESET_REQUEST.ID.eq(id))
      .and(PASSWORD_RESET_REQUEST.USED_AT.isNull)
      .and(PASSWORD_RESET_REQUEST.EXPIRES_AT.gt(now))
      .execute()
}
