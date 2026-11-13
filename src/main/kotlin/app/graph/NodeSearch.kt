package app.graph

import app.utils.ResourceId
import db.tables.references.NODE
import db.tables.references.NODE_PROPERTY
import db.tables.references.NODE_PROPERTY_VALUE
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.DSL.lower

object NodeSearch {
  private val searchVector = DSL.field(DSL.name("search_vector"), String::class.java)

  /**
   * Recomputes the search vector of the node from its title and the values of its (non-deleted) properties.
   * Must be called whenever the title or a property value of the node is written.
   */
  fun refreshSearchVector(
    dsl: DSLContext,
    nodeId: ResourceId,
  ) {
    val propertyValues =
      DSL
        .select(DSL.field("string_agg({0}, ' ')", String::class.java, NODE_PROPERTY_VALUE.VALUE))
        .from(NODE_PROPERTY_VALUE)
        .join(NODE_PROPERTY)
        .on(NODE_PROPERTY.ID.eq(NODE_PROPERTY_VALUE.PROPERTY_ID))
        .where(NODE_PROPERTY_VALUE.NODE_ID.eq(nodeId))
        .and(NODE_PROPERTY.DELETED_AT.isNull)
    val text = DSL.field("concat_ws(' ', {0}, ({1}))", String::class.java, NODE.TITLE, propertyValues)
    // Indexed both as-is and split on non-alphanumerics, so e.g. 'alice@example.com' also matches 'example'
    val vector: Field<String> =
      DSL.field(
        "to_tsvector('simple', {0}) || to_tsvector('simple', regexp_replace({0}, '[^[:alnum:]]+', ' ', 'g'))",
        String::class.java,
        text,
      )

    dsl
      .update(NODE)
      .set(searchVector, vector)
      .where(NODE.ID.eq(nodeId))
      .execute()
  }

  /**
   * Matches nodes where every word of [searchQuery] is a prefix of a word in the title or a property value.
   * Substring matches on the title are kept as well.
   */
  fun matches(searchQuery: String): Condition {
    val titleMatch = lower(NODE.TITLE).likeIgnoreCase(lower("%$searchQuery%"))
    val tsQuery = toPrefixTsQuery(searchQuery) ?: return titleMatch

    return DSL
      .condition("{0} @@ to_tsquery('simple', {1})", searchVector, DSL.`val`(tsQuery))
      .or(titleMatch)
  }

  /**
   * Turns free text into a tsquery string like `'foo':* & 'bar':*`, or null when there are no searchable words.
   */
  fun toPrefixTsQuery(searchQuery: String): String? =
    searchQuery
      .split(Regex("\\s+"))
      .filter { word -> word.any { it.isLetterOrDigit() } }
      .joinToString(" & ") { word -> "'${word.replace("\\", "\\\\").replace("'", "''")}':*" }
      .ifEmpty { null }
}
