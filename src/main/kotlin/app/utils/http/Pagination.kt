package app.utils.http

import app.utils.ResourceId
import kotlinx.serialization.Serializable
import org.jooq.Condition
import org.jooq.Field
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.Base64

const val DEFAULT_PAGE_SIZE = 30

@Serializable
data class PageResponse<T>(
  val items: List<T>,
  val nextCursor: String?,
)

data class Page<T>(
  val items: List<T>,
  val nextCursor: Cursor?,
) {
  fun <R> map(transform: (T) -> R): PageResponse<R> = PageResponse(items.map(transform), nextCursor?.encode())
}

class InvalidCursorException : ClientException("invalid_cursor", "Invalid cursor")

/**
 * Opaque keyset cursor pointing at the last item of a page, for lists ordered by `created_at desc, id asc`.
 */
data class Cursor(
  val createdAt: OffsetDateTime,
  val id: ResourceId,
) {
  fun encode(): String =
    Base64
      .getUrlEncoder()
      .withoutPadding()
      .encodeToString("$createdAt|$id".toByteArray())

  /**
   * Condition selecting the rows that come after this cursor.
   */
  fun after(
    createdAtField: Field<OffsetDateTime?>,
    idField: Field<String?>,
  ): Condition = createdAtField.lt(createdAt).or(createdAtField.eq(createdAt).and(idField.gt(id)))

  companion object {
    fun decode(value: String): Cursor {
      val decoded =
        try {
          String(Base64.getUrlDecoder().decode(value))
        } catch (e: IllegalArgumentException) {
          throw InvalidCursorException()
        }
      val separator = decoded.indexOf('|')
      if (separator < 0) throw InvalidCursorException()

      val createdAt =
        try {
          OffsetDateTime.parse(decoded.substring(0, separator))
        } catch (e: DateTimeParseException) {
          throw InvalidCursorException()
        }
      val id = decoded.substring(separator + 1)
      if (id.isEmpty()) throw InvalidCursorException()

      return Cursor(createdAt = createdAt, id = id)
    }
  }
}

/**
 * Builds a page from rows fetched with a limit of `pageSize + 1`; the extra row only signals that more items exist.
 */
fun <T> toPage(
  rows: List<T>,
  pageSize: Int,
  cursorOf: (T) -> Cursor,
): Page<T> {
  val items = rows.take(pageSize)
  val nextCursor = if (rows.size > pageSize) cursorOf(items.last()) else null
  return Page(items, nextCursor)
}
