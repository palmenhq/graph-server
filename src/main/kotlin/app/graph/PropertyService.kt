package app.graph

import app.graph.validation.DuplicatePropertyException
import app.graph.validation.InvalidPropertyNameException
import app.graph.validation.NoSuchNodeException
import app.graph.validation.NoSuchNodeTypeException
import app.graph.validation.NoSuchPropertyException
import app.graph.validation.PropertyNotOnNodeTypeException
import app.utils.ResourceId
import app.utils.toResourceId
import db.enums.PropertyValueType
import db.tables.pojos.Node
import db.tables.pojos.NodeProperty
import db.tables.pojos.NodePropertyValue
import java.time.OffsetDateTime
import java.util.UUID

class PropertyService(
  private val propertyRepository: PropertyRepository,
  private val graphService: GraphService,
) {
  fun createProperty(
    nodeTypeId: ResourceId,
    name: String,
    valueType: PropertyValueType,
    workspaceId: ResourceId,
  ): NodeProperty {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) throw InvalidPropertyNameException()

    graphService.getNodeTypeById(nodeTypeId = nodeTypeId, workspaceId = workspaceId)
      ?: throw NoSuchNodeTypeException(nodeTypeId)

    if (propertyRepository.findPropertyByName(nodeTypeId, trimmedName, workspaceId) != null) {
      throw DuplicatePropertyException(trimmedName)
    }

    val property =
      NodeProperty(
        id = newPropertyId(),
        nodeTypeId = nodeTypeId,
        name = trimmedName,
        valueType = valueType,
        createdAt = OffsetDateTime.now(),
        workspaceId = workspaceId,
      )
    propertyRepository.createProperty(property)
    return property
  }

  fun getPropertiesByNodeType(
    nodeTypeId: ResourceId,
    workspaceId: ResourceId,
  ): List<NodeProperty> =
    propertyRepository.findPropertiesByNodeType(
      nodeTypeId = nodeTypeId,
      workspaceId = workspaceId,
    )

  /**
   * Sets (or clears, when [value] is null) the value of a property on a node.
   * The property must be defined on the node's type.
   */
  fun setPropertyValue(
    nodeId: ResourceId,
    propertyId: ResourceId,
    value: String?,
    workspaceId: ResourceId,
  ): Pair<NodeProperty, NodePropertyValue> {
    val node = graphService.getNodeById(nodeId = nodeId, workspaceId = workspaceId) ?: throw NoSuchNodeException(nodeId)
    val property =
      propertyRepository.findPropertyById(propertyId = propertyId, workspaceId = workspaceId)
        ?: throw NoSuchPropertyException(propertyId)
    if (property.nodeTypeId != node.type) throw PropertyNotOnNodeTypeException(propertyId, node.type!!)

    val propertyValue =
      NodePropertyValue(
        nodeId = nodeId,
        propertyId = propertyId,
        valueType = property.valueType,
        value = value,
        updatedAt = OffsetDateTime.now(),
        workspaceId = workspaceId,
      )
    propertyRepository.upsertPropertyValue(propertyValue)
    return property to propertyValue
  }

  /**
   * Returns the values of all properties defined on the node's type (null when not set).
   */
  fun getPropertyValuesByNode(
    node: Node,
    workspaceId: ResourceId,
  ): List<Pair<NodeProperty, NodePropertyValue>> =
    propertyRepository.findPropertyValuesByNode(
      nodeId = node.id!!,
      nodeTypeId = node.type!!,
      workspaceId = workspaceId,
    )

  companion object {
    fun newPropertyId() =
      UUID
        .randomUUID()
        .toResourceId("prp")
  }
}
