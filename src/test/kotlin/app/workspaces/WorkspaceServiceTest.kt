package app.workspaces

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkspaceServiceTest {
  private val service = WorkspaceService(WorkspaceRepository(TestHelp.dslContext))

  @Test
  fun `creates and gets workspaces`() {
    val owner = TestHelp.createUser()
    val name = "My workspace"

    val created = service.createWorkspace(name, ownerId = owner.id!!)
    val found = service.getByIdForUser(created.id!!, userId = owner.id!!)!!

    assertEquals(created.name, found.name)
  }

  @Test
  fun `workspaces are only accessible to their owner`() {
    val owner = TestHelp.createUser()
    val other = TestHelp.createUser()

    val workspace = service.createWorkspace("mine", ownerId = owner.id!!)
    val othersWorkspace = service.createWorkspace("theirs", ownerId = other.id!!)

    assertTrue(service.hasAccess(workspace.id!!, owner.id!!))
    assertFalse(service.hasAccess(workspace.id!!, other.id!!))
    assertNull(service.getByIdForUser(workspace.id!!, userId = other.id!!))

    assertEquals(listOf(workspace.id), service.getAllForUser(owner.id!!).map { it.id })
    assertEquals(listOf(othersWorkspace.id), service.getAllForUser(other.id!!).map { it.id })
  }
}
