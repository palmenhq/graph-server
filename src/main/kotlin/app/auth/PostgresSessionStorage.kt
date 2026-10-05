package app.auth

import db.tables.references.SESSION
import io.ktor.server.sessions.SessionStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import java.security.MessageDigest
import java.time.Duration
import java.time.OffsetDateTime
import java.util.HexFormat

/**
 * Stores Ktor sessions in the `session` table. Only a SHA-256 hash of the session token is persisted,
 * so a database leak does not expose usable tokens.
 */
class PostgresSessionStorage(
  private val dsl: DSLContext,
  private val ttl: Duration = DEFAULT_TTL,
) : SessionStorage {
  override suspend fun write(
    id: String,
    value: String,
  ) {
    val now = OffsetDateTime.now()
    withContext(Dispatchers.IO) {
      dsl
        .insertInto(SESSION)
        .set(SESSION.ID, hash(id))
        .set(SESSION.DATA, value)
        .set(SESSION.CREATED_AT, now)
        .set(SESSION.EXPIRES_AT, now.plus(ttl))
        .onConflict(SESSION.ID)
        .doUpdate()
        .set(SESSION.DATA, value)
        .execute()
    }
  }

  override suspend fun invalidate(id: String) {
    withContext(Dispatchers.IO) {
      dsl.deleteFrom(SESSION).where(SESSION.ID.eq(hash(id))).execute()
    }
  }

  override suspend fun read(id: String): String =
    withContext(Dispatchers.IO) {
      dsl
        .select(SESSION.DATA)
        .from(SESSION)
        .where(SESSION.ID.eq(hash(id)))
        .and(SESSION.EXPIRES_AT.gt(OffsetDateTime.now()))
        .fetchOne(SESSION.DATA)
    } ?: throw NoSuchElementException("Session not found or expired")

  fun deleteExpired(): Int =
    dsl
      .deleteFrom(SESSION)
      .where(SESSION.EXPIRES_AT.le(OffsetDateTime.now()))
      .execute()

  private fun hash(id: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(id.toByteArray()))

  companion object {
    val DEFAULT_TTL: Duration = Duration.ofDays(30)
  }
}
