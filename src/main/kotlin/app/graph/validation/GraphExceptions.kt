package app.graph.validation

import app.utils.ResourceId
import app.utils.http.ClientException

class NoSuchNodeTypeException(
  nodeTypeId: ResourceId,
) : ClientException("node_type_not_found", "Node type $nodeTypeId not found")

class NoSuchEdgeTypeException(
  edgeTypeId: ResourceId,
) : ClientException("edge_type_not_found", "Edge type $edgeTypeId not found")

class NoSuchNodeException(
  nodeId: ResourceId,
) : ClientException("node_not_found", "Node $nodeId not found")

class NoSuchEdgeException(
  edgeId: ResourceId,
) : ClientException("edge_not_found", "Edge $edgeId not found")

class NoSuchPropertyException(
  propertyId: ResourceId,
) : ClientException("property_not_found", "Property $propertyId not found")

class DuplicatePropertyException(
  name: String,
) : ClientException("property_already_exists", "Property $name already exists on node type")

class InvalidPropertyNameException : ClientException("invalid_property_name", "Property name must not be blank")

class UnsupportedPropertyValueTypeException(
  valueType: String,
) : ClientException("unsupported_property_value_type", "Property value type $valueType is not supported")

class InvalidPropertyValueException(
  valueType: String,
) : ClientException("invalid_property_value", "Value is not a valid $valueType")

class PropertyNotOnNodeTypeException(
  propertyId: ResourceId,
  nodeTypeId: ResourceId,
) : ClientException("property_not_on_node_type", "Property $propertyId is not defined on node type $nodeTypeId")
