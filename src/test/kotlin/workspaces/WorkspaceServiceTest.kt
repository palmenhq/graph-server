package workspaces

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkspaceServiceTest {
  @Test
  fun `creates and gets workspaces`() {
    val service = WorkspaceService(WorkspaceRepository(TestHelp.dslContext))

    val name = "My workspace"

    val created = service.createWorkspace(name)
    val found = service.getById(created.id!!)!!

    assertEquals(created.name, found.name)
  }
}
