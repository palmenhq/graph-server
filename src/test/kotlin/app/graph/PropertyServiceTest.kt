package app.graph

import TestHelp
import app.graph.validation.DuplicatePropertyException
import app.graph.validation.InvalidPropertyNameException
import app.graph.validation.NoSuchNodeException
import app.graph.validation.NoSuchNodeTypeException
import app.graph.validation.NoSuchPropertyException
import app.graph.validation.PropertyNotOnNodeTypeException
import app.graph.validation.UnsupportedPropertyValueTypeException
import db.enums.PropertyValueType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PropertyServiceTest {
  val graphService = GraphService(GraphRepository(TestHelp.dslContext), TestHelp.workspaceService)
  val propertyService = PropertyService(PropertyRepository(TestHelp.dslContext), graphService)

  @Test
  fun `creates property on node type`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")
    val person = graphService.createNodeType(title = "Person", workspaceId = workspace.id!!)
    val company = graphService.createNodeType(title = "Company", workspaceId = workspace.id!!)

    val property = propertyService.createProperty(person.id!!, "instagramUrl", PropertyValueType.text, workspace.id!!)

    assertEquals("instagramUrl", property.name)
    assertEquals(PropertyValueType.text, property.valueType)
    assertEquals(person.id, property.nodeTypeId)
    assertEquals(listOf(property.id), propertyService.getPropertiesByNodeType(person.id!!, workspace.id!!).map { it.id })
    assertEquals(0, propertyService.getPropertiesByNodeType(person.id!!, otherWorkspace.id!!).size)

    // The same name can be used on another node type, but not twice on the same one
    propertyService.createProperty(company.id!!, "instagramUrl", PropertyValueType.text, workspace.id!!)
    assertThrows(DuplicatePropertyException::class.java) {
      propertyService.createProperty(person.id!!, "instagramUrl", PropertyValueType.text, workspace.id!!)
    }
    assertThrows(InvalidPropertyNameException::class.java) {
      propertyService.createProperty(person.id!!, "  ", PropertyValueType.text, workspace.id!!)
    }
    assertThrows(NoSuchNodeTypeException::class.java) {
      propertyService.createProperty(person.id!!, "other", PropertyValueType.text, otherWorkspace.id!!)
    }
  }

  @Test
  fun `sets, overwrites and clears property value on node`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val person = graphService.createNodeType(title = "Person", workspaceId = workspace.id!!)
    val alice = graphService.createNode(person.id!!, "Alice", workspaceId = workspace.id!!)
    val bob = graphService.createNode(person.id!!, "Bob", workspaceId = workspace.id!!)
    val instagram = propertyService.createProperty(person.id!!, "instagramUrl", PropertyValueType.text, workspace.id!!)

    // Unset properties are returned with a null value
    val unset = propertyService.getPropertyValuesByNode(alice, workspace.id!!).single()
    assertEquals(instagram.id, unset.first.id)
    assertEquals(alice.id, unset.second.nodeId)
    assertEquals(PropertyValueType.text, unset.first.valueType)
    assertEquals(PropertyValueType.text, unset.second.valueType)
    assertNull(unset.second.value)
    assertNull(unset.second.updatedAt)

    val value = propertyService.setPropertyValue(alice.id!!, instagram.id!!, "https://instagram.com/alice", workspace.id!!)
    assertEquals(PropertyValueType.text, value.second.valueType)
    assertEquals(
      listOf("https://instagram.com/alice"),
      propertyService.getPropertyValuesByNode(alice, workspace.id!!).map { it.second.value },
    )

    propertyService.setPropertyValue(alice.id!!, instagram.id!!, "https://instagram.com/alice2", workspace.id!!)
    assertEquals(
      listOf("https://instagram.com/alice2"),
      propertyService.getPropertyValuesByNode(alice, workspace.id!!).map { it.second.value },
    )
    assertEquals(listOf(null), propertyService.getPropertyValuesByNode(bob, workspace.id!!).map { it.second.value })

    propertyService.setPropertyValue(alice.id!!, instagram.id!!, null, workspace.id!!)
    assertNull(
      propertyService
        .getPropertyValuesByNode(alice, workspace.id!!)
        .single()
        .second.value,
    )

    // Properties of other node types are not included
    val company = graphService.createNodeType(title = "Company", workspaceId = workspace.id!!)
    propertyService.createProperty(company.id!!, "website", PropertyValueType.text, workspace.id!!)
    val website2 = propertyService.createProperty(person.id!!, "website", PropertyValueType.text, workspace.id!!)
    assertEquals(
      listOf(website2.id, instagram.id),
      propertyService.getPropertyValuesByNode(alice, workspace.id!!).map { it.second.propertyId },
    )
  }

  @Test
  fun `renames property of node type`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")
    val person = graphService.createNodeType(title = "Person", workspaceId = workspace.id!!)
    val company = graphService.createNodeType(title = "Company", workspaceId = workspace.id!!)
    val alice = graphService.createNode(person.id!!, "Alice", workspaceId = workspace.id!!)
    val instagram = propertyService.createProperty(person.id!!, "instagram", PropertyValueType.text, workspace.id!!)
    val website = propertyService.createProperty(person.id!!, "website", PropertyValueType.text, workspace.id!!)
    propertyService.setPropertyValue(alice.id!!, instagram.id!!, "https://instagram.com/alice", workspace.id!!)

    val renamed = propertyService.renameProperty(person.id!!, instagram.id!!, "  instagramUrl ", workspace.id!!)
    assertEquals(instagram.id, renamed.id)
    assertEquals("instagramUrl", renamed.name)
    assertEquals(
      listOf("instagramUrl", "website"),
      propertyService.getPropertiesByNodeType(person.id!!, workspace.id!!).map { it.name },
    )
    // Values are kept
    val value = propertyService.getPropertyValuesByNode(alice, workspace.id!!).single { it.first.id == instagram.id }
    assertEquals("instagramUrl", value.first.name)
    assertEquals("https://instagram.com/alice", value.second.value)

    // Renaming to its own name is allowed, to another property's name is not
    propertyService.renameProperty(person.id!!, instagram.id!!, "instagramUrl", workspace.id!!)
    assertThrows(DuplicatePropertyException::class.java) {
      propertyService.renameProperty(person.id!!, instagram.id!!, "website", workspace.id!!)
    }
    assertThrows(InvalidPropertyNameException::class.java) {
      propertyService.renameProperty(person.id!!, instagram.id!!, " ", workspace.id!!)
    }
    assertThrows(PropertyNotOnNodeTypeException::class.java) {
      propertyService.renameProperty(company.id!!, instagram.id!!, "x", workspace.id!!)
    }
    assertThrows(NoSuchPropertyException::class.java) {
      propertyService.renameProperty(person.id!!, instagram.id!!, "x", otherWorkspace.id!!)
    }

    propertyService.deleteProperty(person.id!!, website.id!!, workspace.id!!)
    assertThrows(NoSuchPropertyException::class.java) {
      propertyService.renameProperty(person.id!!, website.id!!, "x", workspace.id!!)
    }
    // The name of a deleted property can be reused
    propertyService.renameProperty(person.id!!, instagram.id!!, "website", workspace.id!!)
  }

  @Test
  fun `deletes property from node type`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")
    val person = graphService.createNodeType(title = "Person", workspaceId = workspace.id!!)
    val company = graphService.createNodeType(title = "Company", workspaceId = workspace.id!!)
    val alice = graphService.createNode(person.id!!, "Alice", workspaceId = workspace.id!!)
    val instagram = propertyService.createProperty(person.id!!, "instagramUrl", PropertyValueType.text, workspace.id!!)
    val website = propertyService.createProperty(person.id!!, "website", PropertyValueType.text, workspace.id!!)
    propertyService.setPropertyValue(alice.id!!, instagram.id!!, "https://instagram.com/alice", workspace.id!!)

    assertThrows(PropertyNotOnNodeTypeException::class.java) {
      propertyService.deleteProperty(company.id!!, instagram.id!!, workspace.id!!)
    }
    assertThrows(NoSuchPropertyException::class.java) {
      propertyService.deleteProperty(person.id!!, instagram.id!!, otherWorkspace.id!!)
    }

    val deleted = propertyService.deleteProperty(person.id!!, instagram.id!!, workspace.id!!)
    assertEquals(instagram.id, deleted.id)
    assertNotNull(deleted.deletedAt)
    assertEquals(listOf(website.id), propertyService.getPropertiesByNodeType(person.id!!, workspace.id!!).map { it.id })
    assertEquals(
      listOf(website.id),
      propertyService.getPropertyValuesByNode(alice, workspace.id!!).map { it.first.id },
    )

    assertThrows(NoSuchPropertyException::class.java) {
      propertyService.deleteProperty(person.id!!, instagram.id!!, workspace.id!!)
    }
    assertThrows(NoSuchPropertyException::class.java) {
      propertyService.setPropertyValue(alice.id!!, instagram.id!!, "x", workspace.id!!)
    }

    // The name can be reused after deletion
    propertyService.createProperty(person.id!!, "instagramUrl", PropertyValueType.text, workspace.id!!)
  }

  @Test
  fun `rejects setting property values that do not apply to the node`() {
    val workspace = TestHelp.workspaceService.createWorkspace("test")
    val otherWorkspace = TestHelp.workspaceService.createWorkspace("other")
    val person = graphService.createNodeType(title = "Person", workspaceId = workspace.id!!)
    val company = graphService.createNodeType(title = "Company", workspaceId = workspace.id!!)
    val alice = graphService.createNode(person.id!!, "Alice", workspaceId = workspace.id!!)
    val acme = graphService.createNode(company.id!!, "Acme", workspaceId = workspace.id!!)
    val instagram = propertyService.createProperty(person.id!!, "instagramUrl", PropertyValueType.text, workspace.id!!)

    assertThrows(PropertyNotOnNodeTypeException::class.java) {
      propertyService.setPropertyValue(acme.id!!, instagram.id!!, "x", workspace.id!!)
    }
    assertThrows(NoSuchPropertyException::class.java) {
      propertyService.setPropertyValue(alice.id!!, "invalid", "x", workspace.id!!)
    }
    assertThrows(NoSuchNodeException::class.java) {
      propertyService.setPropertyValue(alice.id!!, instagram.id!!, "x", otherWorkspace.id!!)
    }

    graphService.deleteNode(alice.id!!, workspace.id!!)
    assertThrows(NoSuchNodeException::class.java) {
      propertyService.setPropertyValue(alice.id!!, instagram.id!!, "x", workspace.id!!)
    }
  }
}
