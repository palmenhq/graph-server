package app.graph

import app.utils.ResourceId
import db.tables.daos.NodePropertyDao
import db.tables.pojos.NodeProperty
import db.tables.pojos.NodePropertyValue
import db.tables.references.NODE_PROPERTY
import db.tables.references.NODE_PROPERTY_VALUE
import org.jooq.DSLContext

class PropertyRepository(
  private val dsl: DSLContext,
  private val nodePropertyDao: NodePropertyDao = NodePropertyDao(dsl.configuration()),
) {
  fun createProperty(property: NodeProperty) {
    nodePropertyDao.insert(property)
  }

  fun findPropertyById(
    propertyId: ResourceId,
    workspaceId: ResourceId,
  ): NodeProperty? =
    dsl
      .selectFrom(NODE_PROPERTY)
      .where(NODE_PROPERTY.ID.eq(propertyId))
      .and(NODE_PROPERTY.WORKSPACE_ID.eq(workspaceId))
      .and(NODE_PROPERTY.DELETED_AT.isNull)
      .fetchOneInto(NodeProperty::class.java)

  fun findPropertyByName(
    nodeTypeId: ResourceId,
    name: String,
    workspaceId: ResourceId,
  ): NodeProperty? =
    dsl
      .selectFrom(NODE_PROPERTY)
      .where(NODE_PROPERTY.NODE_TYPE_ID.eq(nodeTypeId))
      .and(NODE_PROPERTY.NAME.eq(name))
      .and(NODE_PROPERTY.WORKSPACE_ID.eq(workspaceId))
      .and(NODE_PROPERTY.DELETED_AT.isNull)
      .fetchOneInto(NodeProperty::class.java)

  fun findPropertiesByNodeType(
    nodeTypeId: ResourceId,
    workspaceId: ResourceId,
  ): List<NodeProperty> =
    dsl
      .selectFrom(NODE_PROPERTY)
      .where(NODE_PROPERTY.NODE_TYPE_ID.eq(nodeTypeId))
      .and(NODE_PROPERTY.WORKSPACE_ID.eq(workspaceId))
      .and(NODE_PROPERTY.DELETED_AT.isNull)
      .orderBy(NODE_PROPERTY.CREATED_AT, NODE_PROPERTY.ID)
      .fetchInto(NodeProperty::class.java)

  /**
   * Inserts the value, or replaces the existing value of the property on the node.
   */
  fun upsertPropertyValue(value: NodePropertyValue) {
    dsl
      .insertInto(NODE_PROPERTY_VALUE)
      .set(NODE_PROPERTY_VALUE.NODE_ID, value.nodeId)
      .set(NODE_PROPERTY_VALUE.PROPERTY_ID, value.propertyId)
      .set(NODE_PROPERTY_VALUE.VALUE_TYPE, value.valueType)
      .set(NODE_PROPERTY_VALUE.VALUE, value.value)
      .set(NODE_PROPERTY_VALUE.UPDATED_AT, value.updatedAt)
      .set(NODE_PROPERTY_VALUE.WORKSPACE_ID, value.workspaceId)
      .onConflict(NODE_PROPERTY_VALUE.NODE_ID, NODE_PROPERTY_VALUE.PROPERTY_ID)
      .doUpdate()
      .set(NODE_PROPERTY_VALUE.VALUE_TYPE, value.valueType)
      .set(NODE_PROPERTY_VALUE.VALUE, value.value)
      .set(NODE_PROPERTY_VALUE.UPDATED_AT, value.updatedAt)
      .execute()
  }

  /**
   * Returns a value for every property defined on the node type, ordered like the properties.
   * Properties that are not set on the node have a null value and updatedAt.
   */
  fun findPropertyValuesByNode(
    nodeId: ResourceId,
    nodeTypeId: ResourceId,
    workspaceId: ResourceId,
  ): List<Pair<NodeProperty, NodePropertyValue>> {
    val nodeProperty = NODE_PROPERTY.`as`("np")
    val value = NODE_PROPERTY_VALUE.`as`("npv")

    return dsl
      .select(*nodeProperty.fields(), *value.fields())
      .from(nodeProperty)
      .leftJoin(value)
      .on(
        value.PROPERTY_ID
          .eq(nodeProperty.ID)
          .and(value.NODE_ID.eq(nodeId)),
      ).where(nodeProperty.NODE_TYPE_ID.eq(nodeTypeId))
      .and(nodeProperty.WORKSPACE_ID.eq(workspaceId))
      .and(nodeProperty.DELETED_AT.isNull)
      .orderBy(nodeProperty.CREATED_AT.desc(), nodeProperty.ID)
      .map { r ->
        r.into(nodeProperty).into(NodeProperty::class.java) to
          NodePropertyValue(
            propertyId = r[nodeProperty.ID],
            nodeId = nodeId,
            value = r[value.VALUE],
            valueType = r[nodeProperty.VALUE_TYPE],
            updatedAt = r[value.UPDATED_AT],
            workspaceId = r[nodeProperty.WORKSPACE_ID],
          )
      }
  }
}
